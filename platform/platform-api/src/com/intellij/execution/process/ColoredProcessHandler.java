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
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.List;

/**
 * @author Roman Chernyatchik
 * @author traff
 */
public class ColoredProcessHandler extends OSProcessHandler implements AnsiEscapeDecoder.ColoredTextAcceptor {
  private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();

  private final List<AnsiEscapeDecoder.ColoredTextAcceptor> myColoredTextListeners = ContainerUtil.newArrayList();

  public ColoredProcessHandler(final GeneralCommandLine commandLine) throws ExecutionException {
    super(commandLine.createProcess(), commandLine.getCommandLineString(), commandLine.getCharset());
    setHasPty(commandLine instanceof PtyCommandLine);
  }

  public ColoredProcessHandler(Process process, String commandLine) {
    super(process, commandLine);
  }

  public ColoredProcessHandler(final Process process,
                               final String commandLine,
                               @NotNull final Charset charset) {
    super(process, commandLine, charset);
  }

  @Override
  public final void notifyTextAvailable(final String text, final Key outputType) {
    myAnsiEscapeDecoder.escapeText(text, outputType, this);
  }

  /**
   * Override this method to handle colored text lines.
   * Overrides should call super.coloredTextAvailable() if they want to pass lines to registered listeners
   * To receive chunks of data instead of fragments inherit your class from ColoredChunksAcceptor interface and
   * override coloredChunksAvailable method.
   * @param text
   * @param attributes
   */
  @Override
  public void coloredTextAvailable(String text, Key attributes) {
    textAvailable(text, attributes);
    notifyColoredListeners(text, attributes);
  }

  protected void notifyColoredListeners(String text, Key attributes) { //TODO: call super.coloredTextAvailable after textAvailable removed
    for (AnsiEscapeDecoder.ColoredTextAcceptor listener: myColoredTextListeners) {
      listener.coloredTextAvailable(text, attributes);
    }
  }

  public void addColoredTextListener(AnsiEscapeDecoder.ColoredTextAcceptor listener) {
    myColoredTextListeners.add(listener);
  }

  public void removeColoredTextListener(AnsiEscapeDecoder.ColoredTextAcceptor listener) {
    myColoredTextListeners.remove(listener);
  }

  /**
   * @deprecated Inheritors should override coloredTextAvailable method
   * or implement {@link com.intellij.execution.process.AnsiEscapeDecoder.ColoredChunksAcceptor}
   * and override method coloredChunksAvailable to process colored chunks.
   * To be removed in IDEA 14.
   */
  protected void textAvailable(final String text, final Key attributes) {
    super.notifyTextAvailable(text, attributes);
  }
}
