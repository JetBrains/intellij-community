// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.logging.KLoggers
import fleet.util.os.OsProcessState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.stream.Collectors

/**
 * [shutdownGracefully] a routine to customize graceful shutdown.
 *   Should return `true` if it was possible to request graceful shutdown, `false` otherwise
 */
class ProcessHolder(private val builder: ProcessBuilder,
                    private val shutdownGracefully: (ProcessHandle) -> Boolean = { it.destroy() },
                    private val handleNonZeroCode: ((Int) -> Unit)? = null) {
  companion object {
    internal val logger = KLoggers.logger(ProcessHolder::class.java)
  }


  val exitCode: CompletableDeferred<Int> = CompletableDeferred()

  private var process = CompletableDeferred<Process>()

  val proc: Deferred<Process> = process

  private val runningState = MutableStateFlow(OsProcessState.NOT_STARTED)
  val state = runningState.asSharedFlow()

  @OptIn(ExperimentalCoroutinesApi::class)
  fun pid(): Long? = kotlin.runCatching { process.getCompleted() }.getOrNull()?.pid()

  suspend fun start(debugName: String) {
    if (!runningState.compareAndSet(OsProcessState.NOT_STARTED, OsProcessState.STARTING)) {
      logger.warn { "Trying to start process once more. State=${runningState.value}" }
      return
    }

    try {
      val process = runInterruptible(Dispatchers.IO) { builder.start() }
      require(runningState.compareAndSet(OsProcessState.STARTING, OsProcessState.RUNNING))
      process.onExit().whenComplete { _, _ ->
        val exitValue = process.exitValue()
        if (exitValue != 0 && runningState.value != OsProcessState.STOPPING) {
          handleNonZeroCode?.invoke(exitValue) ?: logger.error(ManagedProcessAbnormalExitException(
            processName = debugName,
            exitCode = exitValue,
            "Process ${builder.command()} finished with exit code $exitValue. Check log for details."
          ))
        }
        exitCode.complete(exitValue)
        runningState.value = OsProcessState.STOPPED
      }
      this.process.complete(process)
    }
    catch (ex: Throwable) {
      this.process.completeExceptionally(ex)
      throw ex
    }
  }

  fun killNoQuestionsAsked() {
    if (runningState.compareAndSet(OsProcessState.NOT_STARTED, OsProcessState.STOPPED)) {
      logger.info { "'Killing' non-started process" }
      return
    }

    if (runningState.value == OsProcessState.STARTING) {
      logger.debug { "Waiting for process to start before killing..." }
    }

    val process = runBlocking {
      withContext(NonCancellable) {
        process.await()
      }
    }

    if (process.isAlive) {
      runningState.value = OsProcessState.STOPPING
      logger.info { "Forcibly killing process pid=${process.pid()} & ${process.info()}" }
      process.destroyForcibly().waitFor(20, TimeUnit.MILLISECONDS)
      if (process.isAlive) {
        logger.warn { "Process ${process.pid()} & ${process.info()} was not killed! It might be hanging in your system now." }
      }
    }
    else {
      runningState.value = OsProcessState.STOPPED
      logger.info { "Process ${process.pid()} & ${process.info()} was already stopped" }
    }
  }

  suspend fun stopAndWait(gracefulPeriodMs: Long = 120000) {
    val startTimeMillis = System.currentTimeMillis()

    if (runningState.compareAndSet(OsProcessState.NOT_STARTED, OsProcessState.STOPPED)) {
      logger.info { "'Stopping' non-started process" }
      return
    }

    if (runningState.value == OsProcessState.STARTING) {
      logger.debug { "Waiting for process to start before stopping..." }
    }

    withContext(NonCancellable) {
      val process = process.await()
      logger.debug { "Process ${process.pid()} acquired." }

      runningState.value = OsProcessState.STOPPING
      process.toHandle().destroyProcessTree(startTimeMillis, gracefulPeriodMs)
    }
  }

  private suspend fun ProcessHandle.destroyProcessTree(startTimeMillis: Long, gracefulPeriodMs: Long) {
    val processes = descendants().collect(Collectors.toList()) + this
    if (processes.any { it.isAlive }) {
      logger.debug { "Stopping ${pid()} (gracefulPeriodMs=$gracefulPeriodMs) & ${info()}..." }
      processes.forEach { process ->
        if (!shutdownGracefully(process)) {
          logger.warn { "Couldn't request process kill pid=${process.pid()} & ${process.info()}. Forcibly killing" }
          if (!process.destroyForcibly()) {
            logger.error { "Couldn't request process kill forcibly pid=${process.pid()} & ${process.info()}" }
          }
        }
      }
    }

    supervisorScope {
      val elapsedMillis = System.currentTimeMillis() - startTimeMillis
      val remaining = gracefulPeriodMs - elapsedMillis - 50
      processes.filter { process -> process.isAlive }.forEach { process ->
        launch(Dispatchers.IO) {
          try {
            process.onExit().get(remaining, TimeUnit.MILLISECONDS)
          }
          catch (e: TimeoutException) {
            logger.warn { "Forcibly killing process pid=${process.pid()} (after $remaining ms) & ${process.info()} due to timeout of normal termination" }
            if (!process.destroyForcibly()) {
              logger.error { "Couldn't request process kill forcibly pid=${process.pid()} & ${process.info()}" }
            }
          }
        }
      }
    }

    val aliveProcesses = processes.filter { it.isAlive }
    if (aliveProcesses.isNotEmpty()) {
      logger.warn {
        "Processes were not killed! It might be hanging in your system now.\n" +
        aliveProcesses.joinToString("\n") { process -> "[${process.pid()}] ${process.info()}" }
      }
    }
  }
}

class ManagedProcessAbnormalExitException(val processName: String, val exitCode: Int, message: String)
  : Exception("$message\n$processName\n$exitCode")
