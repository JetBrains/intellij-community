// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.google.protobuf.ByteString
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemonRuntimeClasspath
import com.intellij.execution.process.mediator.daemon.ProcessMediatorServer
import com.intellij.execution.process.mediator.rpc.*
import com.intellij.execution.process.mediator.rpc.ProcessMediatorGrpcKt.ProcessMediatorCoroutineStub
import com.intellij.execution.process.mediator.util.ExceptionAsStatus
import com.intellij.execution.process.mediator.util.LoggingClientInterceptor
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.inprocess.InProcessChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.flow.*
import java.io.*
import java.lang.ref.Cleaner
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.collections.ArrayList
import kotlin.coroutines.EmptyCoroutineContext


private val SCOPE = CoroutineScope(EmptyCoroutineContext)

private fun startProcessMediatorDaemon(): ProcessMediatorClient {
  val host = if (java.lang.Boolean.getBoolean("java.net.preferIPv6Addresses")) "::1" else "127.0.0.1"
  val port = 50051

  val daemonCommandLine = createProcessMediatorDaemonCommandLine(host, port.toString())
  val daemonProcessHandler = OSProcessHandler.Silent(daemonCommandLine).apply {
    addProcessListener(object : ProcessAdapter() {
      override fun processTerminated(event: ProcessEvent) {
        println("Daemon exited with code ${event.exitCode}")
      }

      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        println("Daemon [$outputType]: ${event.text}")
      }
    })
  }
  daemonProcessHandler.startNotify()

  val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
  return ProcessMediatorClient(SCOPE, channel)
}

private fun startLocalProcessMediatorClientForTesting(): ProcessMediatorClient {
  val channel = InProcessChannelBuilder.forName("testing").directExecutor().build()
  return ProcessMediatorClient(SCOPE, channel)
}

private fun createProcessMediatorDaemonCommandLine(host: String, port: String): GeneralCommandLine {
  val processMediatorClass = ProcessMediatorDaemonRuntimeClasspath.getMainClass().name
  val classpathClasses = ProcessMediatorDaemonRuntimeClasspath.getClasspathClasses()
  val classpath = classpathClasses.mapNotNullTo(LinkedHashSet()) { it.getResourcePath() }.joinToString(File.pathSeparator)
  val javaVmExecutablePath = SystemProperties.getJavaHome() + File.separator + "bin" + File.separator + "java"

  return GeneralCommandLine(javaVmExecutablePath)
    .withParameters("-cp", classpath)
    .withParameters(processMediatorClass)
    .withParameters(host, port)
}

private fun Class<*>.getResourcePath(): String? {
  return FileUtil.toCanonicalPath(PathManager.getResourceRoot(this, "/" + name.replace('.', '/') + ".class"))
}

private val CLEANER = Cleaner.create()

private class MediatedProcess private constructor(private val handle: MediatedProcessHandle) : Process() {
  companion object {
    fun create(processMediatorClient: ProcessMediatorClient,
               processBuilder: ProcessBuilder): MediatedProcess {
      return create(processMediatorClient,
                    processBuilder.command(),
                    processBuilder.directory() ?: File(".").normalize(),  // defaults to current working directory
                    processBuilder.environment(),
                    processBuilder.redirectInput().file(),
                    processBuilder.redirectOutput().file(),
                    processBuilder.redirectError().file())
    }

    fun create(processMediatorClient: ProcessMediatorClient,
               command: List<String>,
               workingDir: File,
               environVars: Map<String, String>,
               inFile: File?,
               outFile: File?,
               errFile: File?,
    ): MediatedProcess {
      val handle = MediatedProcessHandle(processMediatorClient, command, workingDir, environVars, inFile, outFile, errFile)
      return MediatedProcess(handle).also { process ->
        val cleanable = CLEANER.register(process, handle::close)
        processMediatorClient.registerCleanup(cleanable::clean)
      }
    }
  }

  private val stdin: OutputStream = if (handle.inFile != null) OutputStream.nullOutputStream() else createPipedOutputStream(0)
  private val stdout: InputStream = createPipedInputStream(1)
  private val stderr: InputStream = createPipedInputStream(2)

  private val termination: Deferred<Int> = handle.async {
    handle.rpc {
      processMediatorClient.awaitTermination(pid.await())
    }
  }

  override fun pid(): Long = handle.pid.blockingGet()

  override fun getOutputStream(): OutputStream = stdin
  override fun getInputStream(): InputStream = stdout
  override fun getErrorStream(): InputStream = stderr

  private fun createPipedOutputStream(fd: Int): PipedOutputStream {
    val inputStream = PipedInputStream()
    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    handle.launch(dispatcher) {
      handle.rpc {
        val buffer = ByteArray(8192)

        @Suppress("BlockingMethodInNonBlockingContext", "EXPERIMENTAL_API_USAGE")  // note the .flowOn(Dispatchers.IO) below
        val chunkFlow = flow<ByteString> {
          while (true) {
            val n = inputStream.read(buffer)
            if (n < 0) break
            val chunk = ByteString.copyFrom(buffer, 0, n)
            emit(chunk)
          }
        }.onCompletion {
          inputStream.close()
        }.flowOn(dispatcher)
        processMediatorClient.writeStream(pid.await(), fd, chunkFlow)
      }
    }.invokeOnCompletion {
      dispatcher.close()
    }
    return PipedOutputStream(inputStream)
  }

  private fun createPipedInputStream(fd: Int): PipedInputStream {
    val outputStream = PipedOutputStream()
    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    handle.launch(dispatcher) {
      handle.rpc {
        @Suppress("BlockingMethodInNonBlockingContext")
        outputStream.use { outputStream ->
          processMediatorClient.readStream(pid.await(), fd).collect { chunk ->
            withContext(dispatcher) {
              outputStream.write(chunk.toByteArray())
              outputStream.flush()
            }
          }
        }
      }
    }.invokeOnCompletion {
      dispatcher.close()
    }
    return PipedInputStream(outputStream)
  }

  override fun waitFor(): Int = termination.blockingGet()

  override fun exitValue(): Int {
    return try {
      @Suppress("EXPERIMENTAL_API_USAGE")
      termination.getCompleted()
    }
    catch (e: IllegalStateException) {
      throw IllegalThreadStateException(e.message)
    }
  }

  override fun destroy() {
    destroy(false)
  }

  override fun destroyForcibly(): Process {
    destroy(true)
    return this
  }

  fun destroy(force: Boolean) {
    handle.launch {
      handle.rpc {
        processMediatorClient.destroyProcess(pid.await(), force)
      }
    }
  }
}

/**
 * All remote calls are performed using the provided [ProcessMediatorClient],
 * and the whole process lifecycle is contained within its coroutine scope.
 */
private class MediatedProcessHandle(
  val processMediatorClient: ProcessMediatorClient,
  command: List<String>,
  workingDir: File,
  environVars: Map<String, String>,
  val inFile: File?,
  outFile: File?,
  errFile: File?,
) : CoroutineScope by processMediatorClient.childSupervisorScope(),
    AutoCloseable {

  val pid: Deferred<Long> = async {
    processMediatorClient.createProcess(command, workingDir, environVars, inFile, outFile, errFile)
  }

  private val cleanupJob = launch(start = LAZY) {
    // must be called exactly once;
    // once invoked, the pid is no more valid, and the process must be assumed reaped
    processMediatorClient.release(pid.await())
  }

  /** Controls all operations except CreateProcess() and Release(). */
  private val rpcAwaitingJob = SupervisorJob(coroutineContext[Job])

  suspend fun <R> rpc(block: suspend MediatedProcessHandle.() -> R): R {
    // This might require a bit of explanation.
    //
    // We want to ensure the Release() rpc is not started until any other RPC finishes.
    // This is achieved by making any RPC from within the outer withContext() coroutine
    // (a child of 'rpcAwaitingJob', which means the latter can't complete until all its children do).
    // But at the same time it is desirable to ensure the original caller coroutine still
    // controls the cancellation of the RPC, that is why the original job is restored as a parent using
    // the inner withContext().
    //
    // In fact, the 'rpcAwaitingJob' never gets cancelled explicitly at all,
    // only through the parent scope of MediatedProcessHandle, or finishes using the complete() call in release().
    // It is only used for this single purpose - to ensure strict ordering relation between any RPC call and
    // the final Release() call.
    //
    // In other words, if there was an RW lock for coroutines, this whole thing would be replaced by
    // trying to acquire a read lock in this method, and acquiring a write lock in release().
    val originalJob = currentCoroutineContext()[Job]!!
    return withContext(rpcAwaitingJob) {
      withContext(coroutineContext + originalJob) {
        block()
      }
    }
  }

  /** Once this is invoked, calling any other methods will throw [CancellationException]. */
  suspend fun release() {
    try {
      // let ongoing operations finish gracefully, but don't accept new calls
      rpcAwaitingJob.complete()
      rpcAwaitingJob.join()
    }
    finally {
      cleanupJob.join()
    }
  }

  override fun close() {
    runBlocking {
      release()
    }
  }
}

private class ProcessMediatorClient(
  coroutineScope: CoroutineScope,
  private val channel: ManagedChannel
) : CoroutineScope by coroutineScope.childSupervisorScope(),
    Closeable {
  private val stub = ProcessMediatorCoroutineStub(ClientInterceptors.intercept(channel, LoggingClientInterceptor))

  private val cleanupHooks: MutableList<() -> Unit> = Collections.synchronizedList(ArrayList())

  suspend fun createProcess(command: List<String>, workingDir: File, environVars: Map<String, String>,
                            inFile: File?, outFile: File?, errFile: File?): Long {
    val environVarList = environVars.map { (name, value) ->
      CommandLine.EnvironVar.newBuilder()
        .setName(name)
        .setValue(value)
        .build()
    }
    val commandLine = CommandLine.newBuilder()
      .addAllCommand(command)
      .setWorkingDir(workingDir.absolutePath)
      .addAllEnvironVars(environVarList)
      .apply {
        inFile?.let { setInFile(it.absolutePath) }
        outFile?.let { setOutFile(it.absolutePath) }
        errFile?.let { setErrFile(it.absolutePath) }
      }
      .build()
    val request = CreateProcessRequest.newBuilder().setCommandLine(commandLine).build()
    val response = ExceptionAsStatus.unwrap {
      stub.createProcess(request)
    }
    return response.pid
  }

  suspend fun destroyProcess(pid: Long, force: Boolean) {
    val request = DestroyProcessRequest.newBuilder()
      .setPid(pid)
      .setForce(force)
      .build()
    ExceptionAsStatus.unwrap {
      stub.destroyProcess(request)
    }
  }

  suspend fun awaitTermination(pid: Long): Int {
    val request = AwaitTerminationRequest.newBuilder()
      .setPid(pid)
      .build()
    val reply = ExceptionAsStatus.unwrap {
      stub.awaitTermination(request)
    }
    return reply.exitCode
  }

  fun readStream(pid: Long, fd: Int): Flow<ByteString> {
    val handle = FileHandle.newBuilder()
      .setPid(pid)
      .setFd(fd)
      .build()
    val request = ReadStreamRequest.newBuilder()
      .setHandle(handle)
      .build()
    val chunkFlow = ExceptionAsStatus.unwrap {
      stub.readStream(request)
    }
    return chunkFlow.map { chunk ->
      chunk.buffer
    }.catch { cause ->
      ExceptionAsStatus.unwrap { throw cause }
    }
  }

  suspend fun writeStream(pid: Long, fd: Int, chunkFlow: Flow<ByteString>) {
    val handle = FileHandle.newBuilder()
      .setPid(pid)
      .setFd(fd)
      .build()
    val requests = flow {
      val handleRequest = WriteStreamRequest.newBuilder()
        .setHandle(handle)
        .build()
      emit(handleRequest)

      emitAll(chunkFlow.map { buffer ->
        val chunk = DataChunk.newBuilder()
          .setBuffer(buffer)
          .build()
        WriteStreamRequest.newBuilder()
          .setChunk(chunk)
          .build()
      })
    }
    ExceptionAsStatus.unwrap {
      stub.writeStream(requests)
    }
  }

  suspend fun release(pid: Long) {
    val request = ReleaseRequest.newBuilder()
      .setPid(pid)
      .build()
    ExceptionAsStatus.unwrap { stub.release(request) }
  }

  override fun close() {
    try {
      cleanupHooks.forEach { it() }
    }
    finally {
      cancel()
      channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
  }

  fun registerCleanup(cleanupHook: () -> Unit) {
    cleanupHooks += cleanupHook
  }
}

fun main() {
  //org.apache.log4j.BasicConfigurator.configure()
  val commandLine = GeneralCommandLine("/bin/cat")

  val processMediatorServer = ProcessMediatorServer.createLocalProcessMediatorServerForTesting()
  processMediatorServer.start()

  startLocalProcessMediatorClientForTesting().use { processMediatorClient ->
    val processBuilder = commandLine.toProcessBuilder()
      .redirectInput(File("community/platform/elevation/src/com/intellij/execution/process/elevation/util.kt"))
      .redirectOutput(File("community/platform/elevation/src/com/intellij/execution/process/elevation/util.kt.out"))
      .redirectError(File("community/platform/elevation/src/com/intellij/execution/process/elevation/util.kt.err"))

    val process = MediatedProcess.create(processMediatorClient, processBuilder)
    println("pid: ${process.pid()}")
    OutputStreamWriter(process.outputStream).use {
      it.write("Hello ")
      it.flush()
      Thread.sleep(1000)
      it.write("World\n")
      it.flush()
    }
    process.destroy()
    val output = BufferedReader(InputStreamReader(process.inputStream))
      .lines().collect(Collectors.joining("\n"));
    println("output: ${output}")
    println("waitFor: ${process.waitFor()}")
    println("exitValue: ${process.exitValue()}")
  }


  processMediatorServer.stop()
  processMediatorServer.blockUntilShutdown()
}