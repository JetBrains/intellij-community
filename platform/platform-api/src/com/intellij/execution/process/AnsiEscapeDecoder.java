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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.LineSeparator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

/**
 * See <a href="http://en.wikipedia.org/wiki/ANSI_escape_code">ANSI escape code</a>.
 *
 * @author traff
 */
public class AnsiEscapeDecoder {
  private static final char ESC_CHAR = '\u001B'; // Escape sequence start character
  private static final String CSI = ESC_CHAR + "["; // "Control Sequence Initiator"
  private static final Pattern INNER_PATTERN = Pattern.compile(Pattern.quote("m" + CSI));
  private static final char BACKSPACE = '\b';

  private Key myCurrentTextAttributes;

  /**
   * Parses ansi-color codes from text and sends text fragments with color attributes to textAcceptor
   *
   * @param text         a string with ANSI escape sequences
   * @param outputType   stdout/stderr/system (from {@link ProcessOutputTypes})
   * @param textAcceptor receives text fragments with color attributes.
   *                     It can implement ColoredChunksAcceptor to receive list of pairs (text, attribute).
   */
  public void escapeText(@NotNull String text, @NotNull Key outputType, @NotNull ColoredTextAcceptor textAcceptor) {
    List<Pair<String, Key>> chunks = null;
    int pos = 0;
    text = normalizeAsciiControlCharacters(text);
    while (true) {
      int escSeqBeginInd = text.indexOf(CSI, pos);
      if (escSeqBeginInd < 0) {
        break;
      }
      if (pos < escSeqBeginInd) {
        chunks = processTextChunk(chunks, text.substring(pos, escSeqBeginInd), outputType, textAcceptor);
      }
      final int escSeqEndInd = findEscSeqEndIndex(text, escSeqBeginInd);
      if (escSeqEndInd < 0) {
        break;
      }
      if (text.charAt(escSeqEndInd - 1) == 'm') {
        String escSeq = text.substring(escSeqBeginInd, escSeqEndInd);
        // this is a simple fix for RUBY-8996:
        // we replace several consecutive escape sequences with one which contains all these sequences
        String colorAttribute = INNER_PATTERN.matcher(escSeq).replaceAll(";");
        myCurrentTextAttributes = ColoredOutputTypeRegistry.getInstance().getOutputKey(colorAttribute);
      }
      pos = escSeqEndInd;
    }
    if (pos < text.length()) {
      chunks = processTextChunk(chunks, text.substring(pos), outputType, textAcceptor);
    }
    if (chunks != null && textAcceptor instanceof ColoredChunksAcceptor) {
      ((ColoredChunksAcceptor)textAcceptor).coloredChunksAvailable(chunks);
    }
  }

  @NotNull
  private static String normalizeAsciiControlCharacters(@NotNull String text) {
    int ind = text.indexOf(BACKSPACE);
    if (ind == -1) {
      return text;
    }
    StringBuilder result = new StringBuilder();
    int i = 0;
    int guardIndex = 0;
    boolean removalFromPrevTextAttempted = false;
    while (i < text.length()) {
      LineSeparator lineSeparator = StringUtil.getLineSeparatorAt(text, i);
      if (lineSeparator != null) {
        i += lineSeparator.getSeparatorString().length();
        result.append(lineSeparator.getSeparatorString());
        guardIndex = result.length();
      }
      else {
        if (text.charAt(i) == BACKSPACE) {
          if (result.length() > guardIndex) {
            result.setLength(result.length() - 1);
          }
          else if (guardIndex == 0) {
            removalFromPrevTextAttempted = true;
          }
        }
        else {
          result.append(text.charAt(i));
        }
        i++;
      }
    }
    if (removalFromPrevTextAttempted) {
      // This workaround allows to pretty print progress splitting it into several lines:
      //  25% 1/4 build modules
      //  40% 2/4 build modules
      // instead of one single line "25% 1/4 build modules 40% 2/4 build modules"
      result.insert(0, LineSeparator.LF.getSeparatorString());
    }
    return result.toString();
  }

  /*
   * Selects all consecutive escape sequences and returns escape sequence end index (exclusive).
   * If the escape sequence isn't finished, returns -1.
   */
  private static int findEscSeqEndIndex(@NotNull String text, final int escSeqBeginInd) {
    int beginInd = escSeqBeginInd;
    while (true) {
      int letterInd = findEscSeqLetterIndex(text, beginInd);
      if (letterInd == -1) {
        return beginInd == escSeqBeginInd ? -1 : beginInd;
      }
      if (text.charAt(letterInd) != 'm') {
        return beginInd == escSeqBeginInd ? letterInd + 1 : beginInd;
      }
      beginInd = letterInd + 1;
    }
  }

  private static int findEscSeqLetterIndex(@NotNull String text, int escSeqBeginInd) {
    if (!text.regionMatches(escSeqBeginInd, CSI, 0, CSI.length())) {
      return -1;
    }
    int parameterEndInd = escSeqBeginInd + 2;
    while (parameterEndInd < text.length()) {
      char ch = text.charAt(parameterEndInd);
      if (Character.isDigit(ch) || ch == ';') {
        parameterEndInd++;
      }
      else {
        break;
      }
    }
    if (parameterEndInd < text.length()) {
      char letter = text.charAt(parameterEndInd);
      if (StringUtil.containsChar("ABCDEFGHJKSTfmisu", letter)) {
        return parameterEndInd;
      }
    }
    return -1;
  }

  @Nullable
  private List<Pair<String, Key>> processTextChunk(@Nullable List<Pair<String, Key>> buffer,
                                                   @NotNull String text,
                                                   @NotNull Key outputType,
                                                   @NotNull ColoredTextAcceptor textAcceptor) {
    Key attributes = getCurrentOutputAttributes(outputType);
    if (textAcceptor instanceof ColoredChunksAcceptor) {
      if (buffer == null) {
        buffer = ContainerUtil.newArrayListWithCapacity(1);
      }
      buffer.add(Pair.create(text, attributes));
    }
    else {
      textAcceptor.coloredTextAvailable(text, attributes);
    }
    return buffer;
  }

  @NotNull
  protected Key getCurrentOutputAttributes(@NotNull Key outputType) {
    if (outputType == ProcessOutputTypes.STDERR || outputType == ProcessOutputTypes.SYSTEM) {
      return outputType;
    }
    return myCurrentTextAttributes != null ? myCurrentTextAttributes : outputType;
  }

  public void coloredTextAvailable(@NotNull List<Pair<String, Key>> textChunks, ColoredTextAcceptor textAcceptor) {
    for (Pair<String, Key> textChunk : textChunks) {
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
