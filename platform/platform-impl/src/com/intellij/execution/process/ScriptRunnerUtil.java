/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Elena Shaverdova
 * @author Nikolay Matveev
 */
public final class ScriptRunnerUtil {

  private static final Logger LOG = Logger.getInstance("com.intellij.execution.process.ScriptRunnerUtil");

  public static final Condition<Key> STDOUT_OUTPUT_KEY_FILTER = new Condition<Key>() {
    @Override
    public boolean value(Key key) {
      return ProcessOutputTypes.STDOUT.equals(key);
    }
  };

  public static final Condition<Key> STDERR_OUTPUT_KEY_FILTER = new Condition<Key>() {
    @Override
    public boolean value(Key key) {
      return ProcessOutputTypes.STDERR.equals(key);
    }
  };

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
    return getProcessOutput(new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString()), outputTypeFilter,
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
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        if (outputTypeFilter.value(outputType)) {
          final String text = event.getText();
          outputBuilder.append(text);
          if (LOG.isDebugEnabled()) {
            LOG.debug(text);
          }
        }
      }
    });
    processHandler.startNotify();
    if (!processHandler.waitFor(timeout)) {
      throw new ExecutionException(ExecutionBundle.message("script.execution.timeout", String.valueOf(timeout / 1000)));
    }
    return outputBuilder.toString();
  }

  @Nullable
  private static File getShell() {
    final String shell = System.getenv("SHELL");
    if (shell != null && (shell.contains("bash") || shell.contains("zsh"))) {
      File file = new File(shell);
      if (file.isAbsolute() && file.isFile() && file.canExecute()) {
        return file;
      }
    }
    return null;
  }

  /**
   * Executes a process with given parameters.
   * This method tries to work around the following error:
   * <pre>Cannot run program ...: error=2, No such file or directory</pre>
   * that occurs when {@code exePath} isn't absolute path, so {@code exePath} is searched in PATH environment variable. <p/>
   *
   * There is OSX specific issue that environment variables aren't passed to IDE, if the IDE isn't launched from Terminal.
   * See <a href="http://youtrack.jetbrains.com/issue/IDEA-99154">IDEA-99154</a> . <p/>
   *
   * The workaround for OSX is to execute the command inside shell.
   * For example if {@code exePath} is {@code "nodemon"}, standby command would be:
   * <pre>
   *   /bin/bash -c "nodemon"
   * </pre>
   *
   * This method is to be removed as IDEA-99154 is fixed.
   *
   * @param exePath  path to executable (it must not be absolute)
   * @param workingDirectory
   * @param scriptFile
   * @param parameters
   * @return
   * @throws ExecutionException
   */
  @NotNull
  public static OSProcessHandler executeSafelyOnMac(@NotNull String exePath,
                                                    @Nullable String workingDirectory,
                                                    @Nullable VirtualFile scriptFile,
                                                    @NotNull String[] parameters) throws ExecutionException {
    if (!SystemInfo.isMac) {
      return execute(exePath, workingDirectory, scriptFile, parameters);
    }
    ExecutionException firstException;
    try {
      return execute(exePath, workingDirectory, scriptFile, parameters);
    }
    catch (ExecutionException e) {
      firstException = e;
    }
    File shell = getShell();
    if (shell == null) {
      throw firstException;
    }
    try {
      GeneralCommandLine appCommandLine = new GeneralCommandLine();
      appCommandLine.setExePath(exePath);
      if (scriptFile != null) {
        appCommandLine.addParameter(scriptFile.getPresentableUrl());
      }
      appCommandLine.addParameters(parameters);

      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setPassParentEnvs(true);
      commandLine.setExePath(shell.getAbsolutePath());
      commandLine.addParameter("-c");
      commandLine.addParameter(appCommandLine.getCommandLineString());
      commandLine.setPassFixedPathEnvVarOnMac(true);

      if (workingDirectory != null) {
        commandLine.setWorkDirectory(workingDirectory);
      }

      LOG.info("Standby command line: " + commandLine.getCommandLineString());

      final OSProcessHandler processHandler = new ColoredProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString(),
                                                                        EncodingManager.getInstance().getDefaultCharset());
      if (LOG.isDebugEnabled()) {
        processHandler.addProcessListener(new ProcessAdapter() {
          @Override
          public void onTextAvailable(ProcessEvent event, Key outputType) {
            LOG.debug(outputType + ": " + event.getText());
          }
        });
      }

      return processHandler;
    } catch (ExecutionException e) {
      LOG.info("Standby command failed", e);
      throw firstException;
    }
  }

  @NotNull
  public static OSProcessHandler execute(@NotNull String exePath,
                                         @Nullable String workingDirectory,
                                         @Nullable VirtualFile scriptFile,
                                         String[] parameters) throws ExecutionException {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(exePath);
    commandLine.setPassParentEnvs(true);
    commandLine.setPassFixedPathEnvVarOnMac(true);
    if (scriptFile != null) {
      commandLine.addParameter(scriptFile.getPresentableUrl());
    }
    commandLine.addParameters(parameters);

    if (workingDirectory != null) {
      commandLine.setWorkDirectory(workingDirectory);
    }

    LOG.debug("Command line: " + commandLine.getCommandLineString());
    LOG.debug("Command line env: " + commandLine.getEnvParams());

    final OSProcessHandler processHandler = new ColoredProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString(),
                                                                      EncodingManager.getInstance().getDefaultCharset());
    if (LOG.isDebugEnabled()) {
      processHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(ProcessEvent event, Key outputType) {
          LOG.debug(outputType + ": " + event.getText());
        }
      });
    }

    //ProcessTerminatedListener.attach(processHandler, project);
    return processHandler;
  }

  public static ScriptOutput executeScriptInConsoleWithFullOutput(String exePathString,
                                                                  @Nullable VirtualFile scriptFile,
                                                                  @Nullable String workingDirectory,
                                                                  long timeout,
                                                                  Condition<Key> scriptOutputType,
                                                                  @NonNls String... parameters)
    throws ExecutionException {
    final OSProcessHandler processHandler = execute(exePathString, workingDirectory, scriptFile, parameters);

    ScriptOutput output = new ScriptOutput(scriptOutputType);
    processHandler.addProcessListener(output);
    processHandler.startNotify();

    if (!processHandler.waitFor(timeout)) {
      LOG.warn("Process did not complete in " + timeout / 1000 + "s");
      throw new ExecutionException(ExecutionBundle.message("script.execution.timeout", String.valueOf(timeout / 1000)));
    }
    LOG.debug("script output: " + output.myFilteredOutput);
    return output;
  }

  public static class ScriptOutput extends ProcessAdapter {
    private final Condition<Key> myScriptOutputType;
    public final StringBuilder myFilteredOutput;
    public final StringBuilder myMergedOutput;

    private ScriptOutput(Condition<Key> scriptOutputType) {
      myScriptOutputType = scriptOutputType;
      myFilteredOutput = new StringBuilder();
      myMergedOutput = new StringBuilder();
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
    public void onTextAvailable(ProcessEvent event, Key outputType) {
      final String text = event.getText();
      if (myScriptOutputType.value(outputType)) {
        myFilteredOutput.append(text);
      }
      myMergedOutput.append(text);
    }
  }
}
