// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
      protected @NotNull Key getCurrentOutputAttributes(@NotNull Key outputType) {
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