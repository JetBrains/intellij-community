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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * Capturing process handler that cuts off ansi escapes
 * 
 * @author traff
 */
public class CapturingAnsiEscapesAwareProcessHandler extends CapturingProcessHandler {
  public CapturingAnsiEscapesAwareProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
  }

  /** @deprecated Use {@link #CapturingAnsiEscapesAwareProcessHandler(Process, String)} instead (to be removed in IDEA 17) */
  @SuppressWarnings({"deprecation", "unused"})
  @Deprecated
  public CapturingAnsiEscapesAwareProcessHandler(Process process) {
    super(process);
  }

  public CapturingAnsiEscapesAwareProcessHandler(@NotNull Process process, @NotNull String commandLine) {
    super(process, null, commandLine);
  }

  @Override
  protected CapturingProcessAdapter createProcessAdapter(ProcessOutput processOutput) {
    return new AnsiEscapesAwareAdapter(processOutput);
  }
  
  
  protected static class AnsiEscapesAwareAdapter extends CapturingProcessAdapter implements AnsiEscapeDecoder.ColoredTextAcceptor {
    private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder() {
      @Override
      @NotNull
      protected Key getCurrentOutputAttributes(@NotNull Key outputType) {
        return outputType; //we don't need color information - only stdout and stderr keys are added to output in CapturingProcessAdapter
      }
    };

    public AnsiEscapesAwareAdapter(ProcessOutput output) {
      super(output);
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
      myAnsiEscapeDecoder.escapeText(event.getText(), outputType, this);
    }

    @Override
    public void coloredTextAvailable(@NotNull String text, @NotNull Key attributes) {
      addToOutput(text, attributes);
    }
  }
}