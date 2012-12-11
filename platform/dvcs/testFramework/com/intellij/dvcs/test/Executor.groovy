/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.dvcs.test

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
/**
 *
 * @author Kirill Likhodedov
 */
public class Executor {

  private static String ourCurrentDir

  public static cd(String path) {
    ourCurrentDir = path
    println "cd ${shortenPath(path)}"
  }

  public static String pwd() {
    ourCurrentDir
  }

  public static String touch(String fileName) {
    File file = child(fileName)
    assert !file.exists()
    file.createNewFile()
    println("touch $fileName")
    file.path
  }

  public static String touch(String fileName, String content) {
    String filePath = touch(fileName)
    echo(fileName, content)
    filePath
  }

  public static void echo(String fileName, String content) {
    child(fileName).withWriterAppend("UTF-8") { it.write(content) }
  }

  public static String mkdir(String dirName) {
    File file = child(dirName)
    file.mkdir()
    println("mkdir $dirName")
    file.path
  }

  public static String cat(String fileName) {
    def content = FileUtil.loadFile(child(fileName))
    println("cat fileName")
    content
  }

  public static void cp(String fileName, File destinationDir) {
    FileUtil.copy(child(fileName), new File(destinationDir, fileName))
  }

  protected static String run(List<String> params) {
    final ProcessBuilder builder = new ProcessBuilder().command(params);
    builder.directory(ourCurrentDir());
    builder.redirectErrorStream(true);
    Process clientProcess = builder.start();

    CapturingProcessHandler handler = new CapturingProcessHandler(clientProcess, CharsetToolkit.getDefaultSystemCharset());
    ProcessOutput result = handler.runProcess(30*1000);
    if (result.isTimeout()) {
      throw new RuntimeException("Timeout waiting for Git execution");
    }

    if (result.getExitCode() != 0) {
      log("{" + result.getExitCode() + "}");
    }
    String stdout = result.getStdout().trim();
    if (!StringUtil.isEmpty(stdout)) {
      log(stdout);
    }
    return stdout;
  }

  protected static String findExecutable(String programName, String unixExec, String winExec, Collection<String> pathEnvs) {
    for (String pathEnv : pathEnvs) {
      String exec = System.getenv(pathEnv);
      if (exec != null && new File(exec).canExecute()) {
        log("Using $programName from $pathEnv: $exec");
        return exec;
      }
    }

    String path = System.getenv(SystemInfo.isWindows ? "Path" : "PATH");
    if (path != null) {
      String name = SystemInfo.isWindows ? winExec : unixExec;
      for (String dir : path.split(File.pathSeparator)) {
        File file = new File(dir, name);
        if (file.canExecute()) {
          log("Using $programName from PATH");
          return file.getPath();
        }
      }
    }

    throw new IllegalStateException("$programName executable not found. " +
                                    "Please define a valid environment variable ${pathEnvs.iterator().next()} " +
                                    "pointing to the $programName executable.");
  }

  protected static void log(String msg) {
    println msg
  }

  private static String shortenPath(String path) {
    def split = path.split("/")
    if (split.size() > 3) {
      // split[0] is empty, because the path starts from /
      return "/${split[1]}/.../${split[-2]}/${split[-1]}"
    }
    return path
  }

  private static File child(String fileName) {
    assert ourCurrentDir != null : "Current dir hasn't been initialized yet. Call cd at least once before any other command."
    new File(ourCurrentDir, fileName)
  }

  private static File ourCurrentDir() {
    assert ourCurrentDir != null : "Current dir hasn't been initialized yet. Call cd at least once before any other command."
    new File(ourCurrentDir)
  }

}
