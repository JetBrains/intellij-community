/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public class AnsiEscapeDecoder {
  private static final char TEXT_ATTRS_PREFIX_CH = '\u001B';
  private static final String TEXT_ATTRS_PREFIX = Character.toString(TEXT_ATTRS_PREFIX_CH) + "[";
  private static final String TEXT_ATTRS_PATTERN = "m" + TEXT_ATTRS_PREFIX_CH + "\\[";

  private Key myCurrentColor;

  /**
   * Parses ansi-color codes from text and sends text fragments with color attributes to textAcceptor
   *
   * @param text
   * @param outputType
   * @param textAcceptor if implements ColoredTextAcceptor then it will receive text fragments with color attributes
   *                     if implements ColoredChunksAcceptor then it will receive list of pairs <text, attribute>
   */
  public void escapeText(String text, Key outputType, ColoredTextAcceptor textAcceptor) {
    final List<Pair<String, Key>> textChunks = ContainerUtil.newArrayList();
    int pos = 0;
    while (true) {
      int macroPos = text.indexOf(TEXT_ATTRS_PREFIX, pos);
      if (macroPos < 0) break;
      if (pos != macroPos) {
        textChunks.add(Pair.create(text.substring(pos, macroPos), getCurrentOutputAttributes(outputType)));
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
      textChunks.add(Pair.create(text.substring(pos), getCurrentOutputAttributes(outputType)));
    }
    if (textAcceptor instanceof ColoredChunksAcceptor) {
      ((ColoredChunksAcceptor)textAcceptor).coloredChunksAvailable(textChunks);
    }
    else {
      coloredTextAvailable(textChunks, textAcceptor);
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

  private Key getCurrentOutputAttributes(final Key outputType) {
    if (outputType == ProcessOutputTypes.STDERR || outputType == ProcessOutputTypes.SYSTEM) {
      return outputType;
    }
    return myCurrentColor != null ? myCurrentColor : outputType;
  }

  public void coloredTextAvailable(@NotNull final List<Pair<String, Key>> textChunks, ColoredTextAcceptor textAcceptor) {
    for (final Pair<String, Key> textChunk : textChunks) {
      textAcceptor.coloredTextAvailable(textChunk.getFirst(), textChunk.getSecond());
    }
  }

  public interface ColoredChunksAcceptor extends ColoredTextAcceptor {
    void coloredChunksAvailable(List<Pair<String, Key>> chunks);
  }

  public interface ColoredTextAcceptor {
    void coloredTextAvailable(String text, Key attributes);
  }
}
