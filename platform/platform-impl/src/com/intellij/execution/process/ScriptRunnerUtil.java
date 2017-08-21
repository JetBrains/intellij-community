/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;

public final class ScriptRunnerUtil {

  private static final Logger LOG = Logger.getInstance("com.intellij.execution.process.ScriptRunnerUtil");

  public static final Condition<Key> STDOUT_OUTPUT_KEY_FILTER = key -> ProcessOutputTypes.STDOUT.equals(key);

  public static final Condition<Key> STDERR_OUTPUT_KEY_FILTER = key -> ProcessOutputTypes.STDERR.equals(key);

  public static final Condition<Key> STDOUT_OR_STDERR_OUTPUT_KEY_FILTER = Conditions.or(STDOUT_OUTPUT_KEY_FILTER, STDERR_OUTPUT_KEY_FILTER);

  private static final int DEFAULT_TIMEOUT = 30000;

  private ScriptRunnerUtil() {
  }

  public static String getProcessOutput(@NotNull GeneralCommandLine commandLine)
    throws ExecutionException {
    return getProcessOutput(commandLine, STDOUT_OUTPUT_KEY_FILTER, DEFAULT_TIMEOUT);
  }

  public static String getProcessOutput(@NotNull GeneralCommandLine commandLine, @NotNull Condition<Key> outputTypeFilter, long timeout)
    throws ExecutionException {
    return getProcessOutput(new OSProcessHandler(commandLine), outputTypeFilter,
                            timeout);
  }

  public static String getProcessOutput(@NotNull final ProcessHandler processHandler,
                                        @NotNull final Condition<Key> outputTypeFilter,
                                        final long timeout)
    throws ExecutionException {
    LOG.assertTrue(!processHandler.isStartNotified());
    final StringBuilder outputBuilder = new StringBuilder();
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (outputTypeFilter.value(outputType)) {
          final String text = event.getText();
          outputBuilder.append(text);
          LOG.debug(text);
        }
      }
    });
    processHandler.startNotify();
    if (!processHandler.waitFor(timeout)) {
      throw new ExecutionException(ExecutionBundle.message("script.execution.timeout", String.valueOf(timeout / 1000)));
    }
    return outputBuilder.toString();
  }

  @NotNull
  public static OSProcessHandler execute(@NotNull String exePath,
                                         @Nullable String workingDirectory,
                                         @Nullable VirtualFile scriptFile,
                                         String[] parameters) throws ExecutionException {
    return execute(exePath, workingDirectory, scriptFile, parameters, null);
  }

  @NotNull
  public static OSProcessHandler execute(@NotNull String exePath,
                                         @Nullable String workingDirectory,
                                         @Nullable VirtualFile scriptFile,
                                         String[] parameters,
                                         @Nullable Charset charset) throws ExecutionException {
    GeneralCommandLine commandLine = getBasicCommandLine(exePath);
    if (scriptFile != null) {
      commandLine.addParameter(scriptFile.getPresentableUrl());
    }
    commandLine.addParameters(parameters);

    if (workingDirectory != null) {
      commandLine.setWorkDirectory(workingDirectory);
    }

    LOG.debug("Command line: ", commandLine.getCommandLineString());
    LOG.debug("Command line env: ", commandLine.getEnvironment());

    if (charset == null) {
      charset = EncodingManager.getInstance().getDefaultCharset();
    }
    commandLine.setCharset(charset);
    final OSProcessHandler processHandler = new ColoredProcessHandler(commandLine);
    if (LOG.isDebugEnabled()) {
      processHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          LOG.debug(outputType + ": " + event.getText());
        }
      });
    }

    return processHandler;
  }

  @NotNull
  private static GeneralCommandLine getBasicCommandLine(@NotNull String exePath) {
    exePath = PathEnvironmentVariableUtil.toLocatableExePath(exePath);
    exePath = PathEnvironmentVariableUtil.findExecutableInWindowsPath(exePath);
    return new GeneralCommandLine(exePath);
  }

  public static boolean isExecutableInPath(@NotNull String exePath) {
    String initialExePath = exePath;
    GeneralCommandLine commandLine = getBasicCommandLine(exePath);
    exePath = commandLine.getExePath();

    if (!initialExePath.equals(exePath)) {
      //it was resolved with PathEnvironmentVariableUtil.toLocatableExePath or PathEnvironmentVariableUtil.findExecutableInWindowsPath
      return true;
    }

    String path = commandLine.getEffectiveEnvironment().get("PATH");
    File file = PathEnvironmentVariableUtil.findInPath(exePath, path, null);
    return file != null;
  }

  public static ScriptOutput executeScriptInConsoleWithFullOutput(String exePathString,
                                                                  @Nullable VirtualFile scriptFile,
                                                                  @Nullable String workingDirectory,
                                                                  long timeout,
                                                                  Condition<Key> scriptOutputType,
                                                                  @NonNls String... parameters) throws ExecutionException {
    final OSProcessHandler processHandler = execute(exePathString, workingDirectory, scriptFile, parameters);

    ScriptOutput output = new ScriptOutput(scriptOutputType);
    processHandler.addProcessListener(output);
    processHandler.startNotify();

    if (!processHandler.waitFor(timeout)) {
      LOG.warn("Process did not complete in " + timeout / 1000 + "s");
      throw new ExecutionException(ExecutionBundle.message("script.execution.timeout", String.valueOf(timeout / 1000)));
    }
    LOG.debug("script output: ", output.myFilteredOutput);
    return output;
  }

  public static class ScriptOutput extends ProcessAdapter {
    private final Condition<Key> myScriptOutputType;
    public final StringBuilder myFilteredOutput;
    public final StringBuffer myMergedOutput;

    private ScriptOutput(Condition<Key> scriptOutputType) {
      myScriptOutputType = scriptOutputType;
      myFilteredOutput = new StringBuilder();
      myMergedOutput = new StringBuffer();
    }

    public String getFilteredOutput() {
      return myFilteredOutput.toString();
    }

    public String getMergedOutput() {
      return myMergedOutput.toString();
    }

    public String[] getOutputToParseArray() {
      return getFilteredOutput().split("\n");
    }

    public String getDescriptiveOutput() {
      String outputToParse = getFilteredOutput();
      return StringUtil.isEmpty(outputToParse) ? getMergedOutput() : outputToParse;
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
      final String text = event.getText();
      if (myScriptOutputType.value(outputType)) {
        myFilteredOutput.append(text);
      }
      myMergedOutput.append(text);
    }
  }

  /**
   * Gracefully terminates a process handler.
   * Initially, 'soft kill' is performed (on UNIX it's equivalent to SIGINT signal sending).
   * If the process isn't terminated within a given timeout, 'force quite' is performed (on UNIX it's equivalent to SIGKILL
   * signal sending).
   *
   * @param processHandler {@link ProcessHandler} instance
   * @param millisTimeout timeout in milliseconds between 'soft kill' and 'force quite'
   * @param commandLine command line
   */
  public static void terminateProcessHandler(@NotNull ProcessHandler processHandler,
                                             long millisTimeout,
                                             @Nullable String commandLine) {
    if (processHandler.isProcessTerminated()) {
      if (commandLine == null && processHandler instanceof BaseOSProcessHandler) {
        commandLine = ((BaseOSProcessHandler) processHandler).getCommandLine();
      }
      LOG.warn("Process '" + commandLine + "' is already terminated!");
      return;
    }
    processHandler.destroyProcess();
    if (processHandler instanceof KillableProcess) {
      KillableProcess killableProcess = (KillableProcess) processHandler;
      if (killableProcess.canKillProcess()) {
        if (!processHandler.waitFor(millisTimeout)) {
          // doing 'force quite'
          killableProcess.killProcess();
        }
      }
    }
  }

}
