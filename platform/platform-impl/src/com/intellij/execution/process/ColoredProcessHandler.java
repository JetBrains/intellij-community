// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Set;

/**
 * <p>This process handler supports ANSI coloring.</p>
 * <p>Although it supports the {@link KillableProcessHandler"soft-kill" feature}, it is turned off by default for compatibility reasons.
 * To turn it on either call {@link #setShouldKillProcessSoftly(boolean)}, or extend from {@link KillableColoredProcessHandler}.
 */
public class ColoredProcessHandler extends KillableProcessHandler implements AnsiEscapeDecoder.ColoredTextAcceptor {
  private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();

  public ColoredProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine);
    setShouldKillProcessSoftly(false);
  }

  /**
   * {@code commandLine} must not be not empty (for correct thread attribution in the stacktrace)
   */
  public ColoredProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine) {
    super(process, commandLine);
    setShouldKillProcessSoftly(false);
  }

  /**
   * {@code commandLine} must not be not empty (for correct thread attribution in the stacktrace)
   */
  public ColoredProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @NotNull Charset charset) {
    this(process, commandLine, charset, null);
  }

  /**
   * {@code commandLine} must not be not empty (for correct thread attribution in the stacktrace)
   */
  public ColoredProcessHandler(@NotNull Process process, /*@NotNull*/ String commandLine, @NotNull Charset charset, @Nullable Set<? extends File> filesToDelete) {
    super(process, commandLine, charset, filesToDelete);
    setShouldKillProcessSoftly(false);
  }

  @Override
  public final void notifyTextAvailable(@NotNull final String text, @NotNull final Key outputType) {
    myAnsiEscapeDecoder.escapeText(text, outputType, this);
  }

  /**
   * Override this method to handle colored text lines.
   * Overrides should call super.coloredTextAvailable() if they want to pass lines to registered listeners
   * To receive chunks of data instead of fragments inherit your class from ColoredChunksAcceptor interface and
   * override coloredChunksAvailable method.
   */
  @Override
  public void coloredTextAvailable(@NotNull String text, @NotNull Key attributes) {
    super.notifyTextAvailable(text, attributes);
  }
}
