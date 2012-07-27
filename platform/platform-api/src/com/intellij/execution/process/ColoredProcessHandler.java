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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author Roman Chernyatchik
 */
public class ColoredProcessHandler extends OSProcessHandler {
  public static final char TEXT_ATTRS_PREFIX_CH = '\u001B';
  public static final String TEXT_ATTRS_PREFIX = Character.toString(TEXT_ATTRS_PREFIX_CH) + "[";
  private static final String TEXT_ATTRS_PATTERN = "m" + TEXT_ATTRS_PREFIX_CH + "\\[";

  private Key myCurrentColor;

  public static TextAttributes getByKey(final TextAttributesKey key){
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(key);
  }

  // Registering
  // TODO use new Maia API in ConsoleViewContentType to apply ANSI color changes without
  // restarting RM / Idea + Ruby Plugin

  public ColoredProcessHandler(final GeneralCommandLine commandLine) throws ExecutionException {
    this(commandLine.createProcess(), commandLine.getCommandLineString(), commandLine.getCharset());
  }

  public ColoredProcessHandler(Process process, String commandLine) {
    this(process, commandLine, null);
  }

  public ColoredProcessHandler(final Process process,
                               final String commandLine,
                               @Nullable final Charset charset) {
    super(process, commandLine, charset);
  }

  public final void notifyTextAvailable(final String text, final Key outputType) {
    int pos = 0;
    while(true) {
      int macroPos = text.indexOf(TEXT_ATTRS_PREFIX, pos);
      if (macroPos < 0) break;
      if (pos != macroPos) {
        textAvailable(text.substring(pos, macroPos), getCurrentOutputAttributes(outputType));
      }
      final int macroEndPos = getEndMacroPos(text, macroPos);
      if (macroEndPos < 0) {
        break;
      }
      // this is a simple fix for RUBY-8996:
      // we replace several consecutive escape sequences with one which contains all these sequences
      final String colorAttribute = text.substring(macroPos, macroEndPos).replaceAll(TEXT_ATTRS_PATTERN, ";");
      myCurrentColor = ColoredOutputTypeRegistry.getInstance().getOutputKey(colorAttribute);
      pos = macroEndPos;
    }
    if (pos < text.length()) {
      textAvailable(text.substring(pos), getCurrentOutputAttributes(outputType));
    }
  }

  // selects all consecutive escape sequences
  private static int getEndMacroPos(final String text, int macroPos) {
    int endMacroPos = text.indexOf('m', macroPos);
    while (endMacroPos >= 0) {
      endMacroPos += 1;
      macroPos = text.indexOf(TEXT_ATTRS_PREFIX, endMacroPos);
      if (macroPos != endMacroPos) {
        break;
      }
      endMacroPos = text.indexOf('m', macroPos);
    }
    return endMacroPos;
  }

  protected void textAvailable(final String text, final Key attributes) {
    super.notifyTextAvailable(text, attributes);
  }

  private Key getCurrentOutputAttributes(final Key outputType) {
    if (outputType == ProcessOutputTypes.STDERR) {
      return outputType;
    }
    return myCurrentColor != null ? myCurrentColor : outputType;
  }

}
