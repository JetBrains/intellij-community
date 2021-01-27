// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import org.jetbrains.jps.model.java.JdkVersionDetector

@CompileStatic
class GradleRunner {
  final File gradleProjectDir
  private final String projectDir
  private final BuildMessages messages
  private final String javaHome
  private final List<String> additionalParams

  @Lazy
  private volatile GradleRunner modularGradleRunner = {
    createModularRunner()
  }()

  GradleRunner(
    File gradleProjectDir,
    String projectDir,
    BuildMessages messages,
    String javaHome,
    List<String> additionalParams = getDefaultAdditionalParams()
  ) {
    this.messages = messages
    this.projectDir = projectDir
    this.gradleProjectDir = gradleProjectDir
    this.javaHome = javaHome
    this.additionalParams = additionalParams
  }

  /**
   * Invokes Gradle tasks on {@link #gradleProjectDir} project.
   * Logs error and stops the build process if Gradle process is failed.
   */
  boolean run(String title, String... tasks) {
    return runInner(title, null, false, tasks)
  }

  /**
   * Invokes Gradle tasks on {@code buildFile} project.
   * However, gradle wrapper from project {@link #gradleProjectDir} is used.
   * Logs error and stops the build process if Gradle process is failed.
   */
  boolean run(String title, File buildFile, String... tasks) {
    return runInner(title, buildFile, false, tasks)
  }

  /**
   *
   * @see GradleRunner#run(java.lang.String, java.lang.String [ ])
   */
  boolean runWithModularRuntime(String title, String... tasks) {
    if (isModularRuntime()) return run(title, tasks)
    return modularGradleRunner.run(title, tasks)
  }

  /**
   * Invokes Gradle tasks on {@link #gradleProjectDir} project.
   * Ignores the result of running Gradle.
   */
  boolean forceRun(String title, String... tasks) {
    return runInner(title, null, true, tasks)
  }

  GradleRunner withParams(List<String> additionalParams) {
    return new GradleRunner(gradleProjectDir, projectDir, messages, javaHome, this.additionalParams + additionalParams)
  }

  private static List<String> getDefaultAdditionalParams() {
    def rawParams = System.getProperty("intellij.gradle.jdk.build.parameters", "")
    if (rawParams.isEmpty()) {
      return Collections.emptyList()
    }

    return Arrays.asList(rawParams.split(" "))
  }

  private boolean runInner(String title, File buildFile, boolean force, String... tasks) {
    def result = false
    messages.block("Gradle $tasks") {
      messages.progress(title)
      result = runInner(buildFile, tasks)
      if (!result) {
        def errorMessage = "Failed to complete `gradle ${tasks.join(' ')}`"
        if (force) {
          messages.warning(errorMessage)
        }
        else {
          messages.error(errorMessage)
        }
      }
    }
    return result
  }

  private boolean runInner(File buildFile, String... tasks) {
    def gradleScript = SystemInfo.isWindows ? 'gradlew.bat' : 'gradlew'
    List<String> command = new ArrayList()
    command.add("${gradleProjectDir.absolutePath}/$gradleScript".toString())
    command.add("-Djava.io.tmpdir=${System.getProperty('java.io.tmpdir')}".toString())
    command.add('--stacktrace')
    if (System.getProperty("intellij.build.use.gradle.daemon", "false").toBoolean()) {
      command.add('--daemon')
    }
    else {
      command.add('--no-daemon')
    }
    if (buildFile != null) {
      command.add('-b')
      command.add(buildFile.absolutePath)
    }
    command.addAll(additionalParams)
    command.addAll(tasks)
    def processBuilder = new ProcessBuilder(command).directory(gradleProjectDir)
    processBuilder.environment().put("JAVA_HOME", javaHome)
    def process = processBuilder.start()
    process.consumeProcessOutputStream((OutputStream)System.out)
    process.consumeProcessErrorStream((OutputStream)System.err)
    return process.waitFor() == 0
  }

  private boolean isModularRuntime() {
    return JdkVersionDetector.instance
             .detectJdkVersionInfo(javaHome)
             .@version.feature >= 11
  }

  private GradleRunner createModularRunner() {
    if (isModularRuntime()) {
      return this
    }
    run('Downloading JBR 11', 'setupJbr11')
    def modularRuntime = "$projectDir/build/jdk/11"
    if (SystemInfo.isMac) {
      modularRuntime += '/Contents/Home'
    }
    modularRuntime = FileUtil.toSystemIndependentName(new File(modularRuntime).canonicalPath)
    return new GradleRunner(gradleProjectDir, projectDir, messages, modularRuntime)
  }
}
