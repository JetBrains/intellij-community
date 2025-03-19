// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.io.awaitExit
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.JdkDownloader
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.io.File
import java.nio.file.Path

class GradleRunner(
  private val gradleProjectDir: Path,
  private val options: BuildOptions,
  private val communityRoot: BuildDependenciesCommunityRoot,
  private val additionalParams: List<String> = emptyList(),
  private val withRetries: Boolean = true
) {
  /**
   * Invokes Gradle tasks on [gradleProjectDir] project.
   * Logs error and stops the build process if a Gradle process is failed.
   */
  suspend fun run(title: String, vararg tasks: String): Boolean {
    return runInner(
      title = title,
      buildFile = null,
      force = false,
      parallel = false,
      tasks = tasks.asList())
  }

  suspend fun runInParallel(title: String, vararg tasks: String): Boolean {
    return runInner(
      title = title,
      buildFile = null,
      force = false,
      parallel = true,
      tasks = tasks.asList())
  }

  /**
   * Invokes Gradle tasks on [buildFile] project.
   * However, gradle wrapper from project [gradleProjectDir] is used.
   * Logs error and stops the build process if Gradle process is failed.
   */
  suspend fun run(title: String, buildFile: File, vararg tasks: String): Boolean {
    return runInner(
      title = title,
      buildFile = buildFile,
      force = false,
      parallel = false,
      tasks = tasks.asList(),
    )
  }

  private suspend fun runInner(title: String, buildFile: File?, force: Boolean, parallel: Boolean, tasks: List<String>): Boolean {
    return spanBuilder("gradle $tasks").setAttribute("title", title).use { span ->
      if (runInner(buildFile = buildFile, parallel = parallel, tasks = tasks)) {
        return@use true
      }

      val errorMessage = "Failed to complete `gradle ${tasks.joinToString(separator = " ")}`"
      if (force) {
        span.addEvent(errorMessage)
      }
      else {
        throw RuntimeException(errorMessage)
      }
      false
    }
  }

  private val fileLocks = StripedMutex()

  private suspend fun runInner(buildFile: File?, parallel: Boolean, tasks: List<String>): Boolean {
    val gradleScript = if (SystemInfoRt.isWindows) "gradlew.bat" else "gradlew"
    val command = ArrayList<String>()
    command.add("${gradleProjectDir}/$gradleScript")
    command.add("-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}")
    command.add("-Dorg.gradle.internal.repository.max.retries=${options.resolveDependenciesMaxAttempts}")
    command.add("-Dorg.gradle.internal.repository.max.tentatives=${options.resolveDependenciesMaxAttempts}")
    command.add("-Dorg.gradle.internal.repository.initial.backoff=${options.resolveDependenciesDelayMs}")
    command.add("--stacktrace")
    if (System.getProperty("intellij.build.use.gradle.daemon", "false").toBoolean()) {
      command.add("--daemon")
    }
    else {
      command.add("--no-daemon")
    }

    if (parallel) {
      command.add("--parallel")
    }

    if (buildFile != null) {
      command.add("-b")
      command.add(buildFile.absolutePath)
    }
    command.addAll(additionalParams)
    command.addAll(tasks)
    val gradleCall = suspend {
      val processBuilder = ProcessBuilder(command).directory(gradleProjectDir.toFile())
      processBuilder.environment().put("JAVA_HOME", JdkDownloader.getJdkHome(communityRoot, infoLog = Span.current()::addEvent).toString())
      processBuilder.inheritIO()
      fileLocks.getLock(gradleProjectDir.toString()).withLock {
        withContext(Dispatchers.IO) {
          processBuilder.start().awaitExit() == 0
        }
      }
    }
    return if (withRetries) retryWithExponentialBackOff { gradleCall() } else gradleCall()
  }
}
