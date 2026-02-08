package com.intellij.tools.launch.os

import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.shareIn
import java.io.File
import java.nio.file.Path

sealed interface ProcessOutputInfo {
  data class Piped(val outputFlows: ProcessOutputFlows) : ProcessOutputInfo

  data object InheritedByParent : ProcessOutputInfo

  data class RedirectedToFiles(
    val stdoutLogPath: Path,
    val stderrLogPath: Path,
  ) : ProcessOutputInfo
}

sealed interface ProcessOutputStrategy {
  data object Pipe : ProcessOutputStrategy

  data object InheritIO : ProcessOutputStrategy

  data class RedirectToFiles(val logFolder: File) : ProcessOutputStrategy
}

fun ProcessBuilder.affixIO(strategy: ProcessOutputStrategy): CoroutineScope.(Process) -> ProcessOutputInfo {
  return when (strategy) {
    ProcessOutputStrategy.Pipe -> {
      { process -> ProcessOutputInfo.Piped(process.produceOutputFlows(coroutineScope = this)) }
    }
    ProcessOutputStrategy.InheritIO -> {
      this.inheritIO()
      return { ProcessOutputInfo.InheritedByParent }
    }
    is ProcessOutputStrategy.RedirectToFiles -> {
      strategy.logFolder.mkdirs()
      val ts = System.currentTimeMillis()
      val stdoutFile = strategy.logFolder.resolve("out-$ts.log")
      val stderrFile = strategy.logFolder.resolve("err-$ts.log")
      this.redirectOutput(stdoutFile)
      this.redirectError(stderrFile)
      return { ProcessOutputInfo.RedirectedToFiles(stdoutFile.toPath(), stderrFile.toPath()) }
    }
  }
}

fun Process.asyncAwaitExit(lifespanScope: CoroutineScope, processTitle: String): Deferred<Int> =
  lifespanScope.async(Dispatchers.IO + SupervisorJob() + CoroutineName("$processTitle | await for termination")) {
    awaitExit()
  }

data class ProcessOutputFlows(val stdout: Flow<String>, val stderr: Flow<String>)

fun Process.produceOutputFlows(coroutineScope: CoroutineScope): ProcessOutputFlows =
  ProcessOutputFlows(
    stdout = inputStream
      .bufferedReader()
      .lineSequence()
      .asFlow()
      .shareIn(coroutineScope, SharingStarted.Lazily),
    stderr = errorStream
      .bufferedReader()
      .lineSequence()
      .asFlow()
      .shareIn(coroutineScope, SharingStarted.Lazily),
  )
