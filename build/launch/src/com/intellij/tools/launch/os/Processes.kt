package com.intellij.tools.launch.os

import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.io.File
import java.nio.file.Path

sealed interface ProcessOutputInfo {
  data object Piped : ProcessOutputInfo

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

fun ProcessBuilder.affixIO(strategy: ProcessOutputStrategy): ProcessOutputInfo =
  when (strategy) {
    ProcessOutputStrategy.Pipe -> ProcessOutputInfo.Piped
    ProcessOutputStrategy.InheritIO -> {
      this.inheritIO()
      ProcessOutputInfo.InheritedByParent
    }
    is ProcessOutputStrategy.RedirectToFiles -> {
      strategy.logFolder.mkdirs()
      val ts = System.currentTimeMillis()
      val stdoutFile = strategy.logFolder.resolve("out-$ts.log")
      val stderrFile = strategy.logFolder.resolve("err-$ts.log")
      this.redirectOutput(stdoutFile)
      this.redirectError(stderrFile)
      ProcessOutputInfo.RedirectedToFiles(stdoutFile.toPath(), stderrFile.toPath())
    }
  }

suspend fun Process.asyncAwaitExit(coroutineScope: CoroutineScope, processTitle: String): Deferred<Int> =
  coroutineScope.async(Dispatchers.IO + SupervisorJob() + CoroutineName("$processTitle | await for termination")) {
    awaitExit()
  }