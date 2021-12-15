// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic
import io.opentelemetry.api.trace.Span

import java.util.function.Supplier

@CompileStatic
final class GradleRunner {
  final File gradleProjectDir
  private final String projectDir
  private final BuildMessages messages
  private final List<String> additionalParams
  private final BuildOptions options

  GradleRunner(
    File gradleProjectDir,
    String projectDir,
    BuildMessages messages,
    BuildOptions options,
    List<String> additionalParams = getDefaultAdditionalParams()
  ) {
    this.messages = messages
    this.options = options
    this.projectDir = projectDir
    this.gradleProjectDir = gradleProjectDir
    this.additionalParams = additionalParams
  }

  /**
   * Invokes Gradle tasks on {@link #gradleProjectDir} project.
   * Logs error and stops the build process if Gradle process is failed.
   */
  boolean run(String title, String... tasks) {
    return runInner(title, null, false, false, tasks)
  }

  boolean runInParallel(String title, String... tasks) {
    return runInner(title, null, false, true, tasks)
  }

  /**
   * Invokes Gradle tasks on {@code buildFile} project.
   * However, gradle wrapper from project {@link #gradleProjectDir} is used.
   * Logs error and stops the build process if Gradle process is failed.
   */
  boolean run(String title, File buildFile, String... tasks) {
    return runInner(title, buildFile, false, false, tasks)
  }

  GradleRunner withParams(List<String> additionalParams) {
    return new GradleRunner(gradleProjectDir, projectDir, messages, options, this.additionalParams + additionalParams)
  }

  private static List<String> getDefaultAdditionalParams() {
    def rawParams = System.getProperty("intellij.gradle.jdk.build.parameters", "")
    if (rawParams.isEmpty()) {
      return Collections.emptyList()
    }

    return Arrays.asList(rawParams.split(" "))
  }

  boolean runOneTask(String task) {
    boolean result = runInner(null, false, task)
    if (!result) {
      messages.error("Failed to complete `gradle $task`")
    }
    return result
  }

  private boolean runInner(String title, File buildFile, boolean force, boolean parallel, String... tasks) {
    return messages.block("Gradle $tasks", new Supplier<Boolean>() {
      @Override
      Boolean get() {
        Span.current().addEvent(title)
        if (runInner(buildFile, parallel, tasks)) {
          return Boolean.TRUE
        }

        String errorMessage = "Failed to complete `gradle ${String.join(" ", tasks)}`"
        if (force) {
          messages.warning(errorMessage)
        }
        else {
          messages.error(errorMessage)
        }
        return Boolean.FALSE
      }
    }) == Boolean.TRUE
  }

  private boolean runInner(File buildFile, boolean parallel, String... tasks) {
    String gradleScript = SystemInfoRt.isWindows ? "gradlew.bat" : "gradlew"
    List<String> command = new ArrayList()
    command.add("${gradleProjectDir.absolutePath}/$gradleScript".toString())
    command.add("-Djava.io.tmpdir=${System.getProperty('java.io.tmpdir')}".toString())
    command.add("-Dorg.gradle.internal.repository.max.tentatives=${options.resolveDependenciesMaxAttempts}".toString())
    command.add("-Dorg.gradle.internal.repository.initial.backoff=${options.resolveDependenciesDelayMs}".toString())
    command.add('--stacktrace')
    if (System.getProperty("intellij.build.use.gradle.daemon", "false").toBoolean()) {
      command.add('--daemon')
    }
    else {
      command.add('--no-daemon')
    }

    if (parallel) {
      command.add("--parallel")
    }

    if (buildFile != null) {
      command.add('-b')
      command.add(buildFile.absolutePath)
    }
    command.addAll(additionalParams)
    command.addAll(tasks)
    def processBuilder = new ProcessBuilder(command).directory(gradleProjectDir)
    processBuilder.environment().put("JAVA_HOME", SystemProperties.javaHome)
    def process = processBuilder.start()
    process.consumeProcessOutputStream((OutputStream)System.out)
    process.consumeProcessErrorStream((OutputStream)System.err)
    return process.waitFor() == 0
  }
}
