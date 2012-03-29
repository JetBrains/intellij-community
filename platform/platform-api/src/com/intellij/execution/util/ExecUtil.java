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
package com.intellij.execution.util;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.script.ScriptException;
import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ExecUtil {
  private ExecUtil() { }

  public static int execAndGetResult(final String... command) throws ExecutionException, InterruptedException {
    assert command != null && command.length > 0;
    return execAndGetResult(Arrays.asList(command));
  }

  public static int execAndGetResult(@NotNull final List<String> command) throws ExecutionException, InterruptedException {
    assert command.size() > 0;
    final GeneralCommandLine commandLine = new GeneralCommandLine(command);
    final Process process = commandLine.createProcess();
    return process.waitFor();
  }

  public static String loadTemplate(@NotNull final ClassLoader loader,
                                    @NotNull final String templateName,
                                    @Nullable final Map<String, String> variables) throws IOException {
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") final InputStream stream = loader.getResourceAsStream(templateName);
    if (stream == null) {
      throw new IOException("Template '" + templateName + "' not found by " + loader);
    }

    final String template = FileUtil.loadTextAndClose(stream);
    if (variables == null || variables.size() == 0) {
      return template;
    }

    final StringBuilder buffer = new StringBuilder(template);
    for (Map.Entry<String, String> var : variables.entrySet()) {
      final String name = var.getKey();
      final int pos = buffer.indexOf(name);
      if (pos >= 0) {
        buffer.replace(pos, pos + name.length(), var.getValue());
      }
    }
    return buffer.toString();
  }

  public static File createTempExecutableScript(@NotNull final String prefix,
                                                @NotNull final String suffix,
                                                @NotNull final String source) throws IOException, ExecutionException {
    final File tempFile = FileUtil.createTempFile(prefix, suffix);
    FileUtil.writeToFile(tempFile, source);
    if (!tempFile.setExecutable(true, true)) {
      throw new ExecutionException("Failed to make temp file executable: " + tempFile);
    }
    return tempFile;
  }

  public static String getOsascriptPath() {
    return "/usr/bin/osascript";
  }

  public static String getOpenCommandPath() {
    return "/usr/bin/open";
  }

  public static ProcessOutput execAndGetOutput(@NotNull final List<String> command, @Nullable final String workDir) throws ExecutionException, InterruptedException {
    assert command.size() > 0;
    final GeneralCommandLine commandLine = new GeneralCommandLine(command);
    if (workDir != null)
      commandLine.setWorkDirectory(workDir);
    final Process process = commandLine.createProcess();
    CapturingProcessHandler processHandler = new CapturingProcessHandler(process);
    return processHandler.runProcess();
  }

  @Nullable
  public static String execAndReadLine(final String... command) {
    try {
      final Process process = new GeneralCommandLine(command).createProcess();
      final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      try {
        return reader.readLine();
      }
      finally {
        reader.close();
      }
    }
    catch (Exception ignored) { }
    return null;
  }

  public static ProcessOutput sudoAndGetOutput(@NotNull final String scriptPath,
                                               @NotNull final String prompt) throws IOException, ExecutionException, ScriptException, InterruptedException {
    return sudoAndGetOutput(scriptPath, prompt, null);
  }

  public static ProcessOutput sudoAndGetOutput(@NotNull final String scriptPath,
                                               @NotNull final String prompt, @Nullable String workDir) throws IOException, ExecutionException, ScriptException, InterruptedException {
    if (SystemInfo.isMac) {
      final String script = "do shell script \"" + scriptPath + "\" with administrator privileges";
      Runtime runtime = Runtime.getRuntime();
      String[] args = {getOsascriptPath(), "-e", script};
      if (workDir != null)
        runtime.exec(args, ArrayUtil.EMPTY_STRING_ARRAY, new File(workDir));
      else
        runtime.exec(args);
      return new ProcessOutput(0);
    }
    else if (SystemInfo.isKDE) {
      return execAndGetOutput(Lists.<String>newArrayList("kdesudo", "--comment", prompt, scriptPath), workDir);
    }
    else if (SystemInfo.isGnome) {
      return execAndGetOutput(Lists.<String>newArrayList("gksudo", "--message", prompt, scriptPath), workDir);
    }
    else if (SystemInfo.isUnix) {
      final File sudo = createTempExecutableScript("sudo", ".sh",
                                                   "#!/bin/sh\n" +
                                                   "echo \"" + prompt + "\"\n" +
                                                   "echo\n" +
                                                   "sudo \"" + scriptPath + "\"\n" +
                                                   "STATUS=$?" +
                                                   "echo\n" +
                                                   "read -p \"Press Enter to close this window...\" TEMP\n" +
                                                   "exit $STATUS\n");
      return execAndGetOutput(Lists.<String>newArrayList("xterm", "-T", "Install", "-e", sudo.getAbsolutePath()), workDir);
    }
    else {
      throw new UnsupportedOperationException("Unsupported OS/desktop: " + SystemInfo.OS_NAME + '/' + SystemInfo.SUN_DESKTOP);
    }
  }

  public static int sudoAndGetResult(@NotNull final String scriptPath,
                                     @NotNull final String prompt) throws IOException, ExecutionException, ScriptException, InterruptedException {
    return sudoAndGetOutput(scriptPath, prompt).getExitCode();
  }
}
