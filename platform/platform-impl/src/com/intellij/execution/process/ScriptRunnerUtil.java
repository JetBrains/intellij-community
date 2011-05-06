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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lene
 * Date: 26.04.11
 * Time: 23:10
 */
public class ScriptRunnerUtil {
  private static final Logger LOG = Logger.getInstance(ScriptRunnerUtil.class.getName());

  private ScriptRunnerUtil() {
  }

  public static OSProcessHandler execute(String exePath,
                                         @Nullable String workingDirectory,
                                         @Nullable VirtualFile scriptFile,
                                         String[] parameters) throws ExecutionException {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(exePath);
    commandLine.setPassParentEnvs(true);
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
    processHandler.startNotify();
    //ProcessTerminatedListener.attach(processHandler, project);
    return processHandler;
  }

  public static Pair<String, String[]> getExePathAndCommand(List<String> commandToExecute) {
    @NonNls String exePath = "";
    @NonNls List<String> parameters = new ArrayList<String>();
    if (SystemInfo.isOS2) {
      exePath = "cmd";
      parameters.add("/c");
    }
    else if (SystemInfo.isWindows) {
      if (!SystemInfo.isWindows9x) {
        exePath = "cmd";
      }
      else {
        exePath = "command.com";
      }
      parameters.add("/c");

      /*if exe-path and at least one of parameters contain spaces the whole command must be quoted,
         e.g. instead of
            cmd /c "C:\Program Files\jdk1.5\bin\java" "-Dproperty=a b c" myclass
         must be used
            cmd /c ""C:\Program Files\jdk1.5\bin\java" "-Dproperty=a b c" myclass"
      */
      boolean quoteWholeCommand = false;
      for (int i = 1; i < commandToExecute.size(); i++) {
        quoteWholeCommand |= commandToExecute.get(i).contains(" ");
      }
      quoteWholeCommand &= commandToExecute.size() > 0 && commandToExecute.get(0).contains(" ");

      if (quoteWholeCommand) {
        StringBuilder builder = new StringBuilder();
        builder.append('"');
        for (int i = 0; i < commandToExecute.size(); i++) {
          if (i > 0) {
            builder.append(' ');
          }
          builder.append(GeneralCommandLine.quote(commandToExecute.get(i)));
        }
        builder.append('"');

        parameters.add(builder.toString());
        return Pair.create(exePath, ArrayUtil.toStringArray(parameters));
      }
    }

    int firstParamIdx = 0;
    if (SystemInfo.isUnix) {
      exePath = commandToExecute.get(0);
      firstParamIdx = 1;
    }

    for (int i = firstParamIdx; i < commandToExecute.size(); i++) {
      parameters.add(commandToExecute.get(i));
    }

    return Pair.create(exePath, ArrayUtil.toStringArray(parameters));
  }

  public static ScriptOutput executeScriptInConsoleWithFullOutput(String exePathString,
                                                                  @Nullable VirtualFile scriptFile,
                                                                  @Nullable String workingDirectory,
                                                                  long timeout,
                                                                  ScriptOutputType scriptOutputType,
                                                                  @NonNls String... parameters)
    throws ExecutionException {
    final OSProcessHandler processHandler = execute(exePathString, workingDirectory, scriptFile, parameters);

    final StringBuilder standardOutput = scriptOutputType.readStandardOutput() ? new StringBuilder() : null;
    final StringBuilder errorOutput = scriptOutputType.readErrorOutput() ? new StringBuilder() : null;
    final StringBuilder mergedOutput =
      (scriptOutputType.readStandardOutput() && scriptOutputType.readErrorOutput()) ? new StringBuilder() : null;
    addReadingProcessListener(scriptOutputType, processHandler, standardOutput, errorOutput, mergedOutput);

    if (!processHandler.waitFor(timeout)) {
      LOG.warn("Process did not complete in " + timeout / 1000 + "s");
      throw new ExecutionException(ExecutionBundle.message("script.execution.timeout", String.valueOf(timeout / 1000)));
    }
    LOG.debug("script output: " + standardOutput);
    return new ScriptOutput(scriptOutputType, standardOutput, errorOutput, mergedOutput);
  }

  private static void checkOutputs(ScriptOutputType scriptOutputType,
                                   StringBuilder standardOutput,
                                   StringBuilder errorOutput,
                                   StringBuilder mergedOutput) {
    if (scriptOutputType.readStandardOutput()) {
      LOG.assertTrue(standardOutput != null);
    }
    if (scriptOutputType.readErrorOutput()) {
      LOG.assertTrue(errorOutput != null);
      if (scriptOutputType.readStandardOutput()) {
        LOG.assertTrue(mergedOutput != null);
      }
    }
  }

  private static void addReadingProcessListener(final ScriptOutputType scriptOutputType,
                                                OSProcessHandler processHandler,
                                                @Nullable final StringBuilder standardOutput,
                                                @Nullable final StringBuilder errorOutput,
                                                @Nullable final StringBuilder mergedOutput) {
    checkOutputs(scriptOutputType, standardOutput, errorOutput, mergedOutput);

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        if (outputType == ProcessOutputTypes.STDOUT && scriptOutputType.readStandardOutput()) {
          LOG.assertTrue(standardOutput != null);
          standardOutput.append(event.getText());
        }
        else if (outputType == ProcessOutputTypes.STDERR && scriptOutputType.readErrorOutput()) {
          LOG.assertTrue(errorOutput != null);
          errorOutput.append(event.getText());
        }
        if (scriptOutputType.readStandardOutput() && scriptOutputType.readErrorOutput()) {
          LOG.assertTrue(mergedOutput != null);
          mergedOutput.append(event.getText());
        }
      }
    });
  }

  public static enum ScriptOutputType {
    Standard {
      @Override
      public boolean readStandardOutput() {
        return true;
      }

      @Override
      public boolean readErrorOutput() {
        return false;
      }
    }, Error {
      @Override
      public boolean readStandardOutput() {
        return false;
      }

      @Override
      public boolean readErrorOutput() {
        return true;
      }
    }, StandardAndError {
      @Override
      public boolean readStandardOutput() {
        return true;
      }

      @Override
      public boolean readErrorOutput() {
        return true;
      }
    };

    public abstract boolean readStandardOutput();

    public abstract boolean readErrorOutput();
  }

  public static class ScriptOutput {
    private final ScriptOutputType myScriptOutputType;
    public final StringBuilder myStandardOutput;
    public final StringBuilder myErrorOutput;
    public final StringBuilder myMergedOutput;

    private ScriptOutput(ScriptOutputType scriptOutputType,
                         @Nullable StringBuilder standardOutput,
                         @Nullable StringBuilder errorOutput,
                         @Nullable StringBuilder mergedOutput) {
      checkOutputs(scriptOutputType, standardOutput, errorOutput, mergedOutput);
      myScriptOutputType = scriptOutputType;
      myStandardOutput = standardOutput;
      myErrorOutput = errorOutput;
      myMergedOutput = mergedOutput;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public String getErrorOutput() {
      LOG.assertTrue(myScriptOutputType.readErrorOutput());
      return myErrorOutput.toString();
    }

    public String getStandardOutput() {
      LOG.assertTrue(myScriptOutputType.readStandardOutput());
      return myStandardOutput.toString();
    }

    public String getMergedOutput() {
      LOG.assertTrue(myScriptOutputType.readStandardOutput());
      LOG.assertTrue(myScriptOutputType.readErrorOutput());
      return myMergedOutput.toString();
    }
  }
}
