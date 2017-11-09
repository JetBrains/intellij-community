/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

import com.intellij.openapi.util.SystemInfo
import groovy.transform.CompileStatic

@CompileStatic
class GradleRunner {
  private final File projectDir
  private final BuildMessages messages
  private final String javaHome

  GradleRunner(File projectDir, BuildMessages messages, String javaHome) {
    this.messages = messages
    this.projectDir = projectDir
    this.javaHome = javaHome
  }

  /**
   * Invokes Gradle tasks on {@link #projectDir} project.
   * Logs error and stops the build process if Gradle process is failed.
   */
  boolean run(String title, String... tasks) {
    return runInner(title, false, tasks)
  }
  
  /**
   * Invokes Gradle tasks on {@link #projectDir} project.
   * Ignores the result of running Gradle.
   */
  boolean forceRun(String title, String... tasks) {
    return runInner(title, true, tasks)
  }

  private boolean runInner(String title, boolean force, String... tasks) {
    def result = false
    messages.block("Gradle $tasks") {
      messages.progress(title)
      result = runInner(tasks)
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

  private boolean runInner(String... tasks) {
    def gradleScript = SystemInfo.isWindows ? 'gradlew.bat' : 'gradlew'
    List<String> command = new ArrayList()
    command.add("${projectDir.absolutePath}/$gradleScript".toString())
    command.addAll(tasks)
    command.add('--stacktrace')
    command.add('--no-daemon')
    def processBuilder = new ProcessBuilder(command).directory(projectDir)
    processBuilder.environment().put("JAVA_HOME", javaHome)
    def process = processBuilder.start()
    process.consumeProcessOutputStream((OutputStream)System.out)
    process.consumeProcessErrorStream((OutputStream)System.err)
    return process.waitFor() == 0
  }
}
