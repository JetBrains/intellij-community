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

/**
 * See <a href="http://en.wikipedia.org/wiki/ANSI_escape_code">ANSI escape code</a>.
 *
 * @author traff
 */
public class AnsiEscapeDecoder {
  private static final char ESC_CHAR = '\u001B'; // Escape sequence start character
  private static final String CSI = ESC_CHAR + "["; // "Control Sequence Initiator"
  private static final String M_CSI = "m" + CSI;
  private static final char BACKSPACE = '\b';

  private final ColoredOutputTypeRegistry myColoredOutputTypeRegistry = ColoredOutputTypeRegistry.getInstance();
  private Key myCurrentTextAttributes;
  private String myUnhandledStdout;
  private String myUnhandledStderr;

  /**
   * Parses ansi-color codes from text and sends text fragments with color attributes to textAcceptor
   *
   * @param text         a string with ANSI escape sequences
   * @param outputType   stdout/stderr/system (from {@link ProcessOutputTypes})
   * @param textAcceptor receives text fragments with color attributes.
   *                     It can implement ColoredChunksAcceptor to receive list of pairs (text, attribute).
   */
  public void escapeText(@NotNull String text, @NotNull Key outputType, @NotNull ColoredTextAcceptor textAcceptor) {
    text = prependUnhandledText(text, outputType);
    text = normalizeAsciiControlCharacters(text);
    int pos = 0;
    List<Pair<String, Key>> chunks = null;
    int unhandledSuffixLength = 0;
    while (true) {
      int escSeqBeginInd = findEscSeqBeginIndex(text, pos);
      if (escSeqBeginInd < 0) {
        if (escSeqBeginInd < -1) {
          unhandledSuffixLength = decodeUnhandledSuffixLength(escSeqBeginInd);
        }
        break;
      }
      if (pos < escSeqBeginInd) {
        chunks = processTextChunk(chunks, text.substring(pos, escSeqBeginInd), outputType, textAcceptor);
      }
      int escSeqEndInd = findConsecutiveEscSequencesEndIndex(text, escSeqBeginInd);
      if (escSeqEndInd < 0) {
        if (escSeqEndInd < -1) {
          unhandledSuffixLength = decodeUnhandledSuffixLength(escSeqEndInd);
        }
        break;
      }
      if (text.charAt(escSeqEndInd) == 'm') {
        String escSeq = text.substring(escSeqBeginInd, escSeqEndInd + 1);
        // this is a simple fix for RUBY-8996:
        // we replace several consecutive escape sequences with one which contains all these sequences
        String colorAttribute = StringUtil.replace(escSeq, M_CSI, ";");
        myCurrentTextAttributes = myColoredOutputTypeRegistry.getOutputKey(colorAttribute);
      }
      pos = escSeqEndInd + 1;
    }
    updateUnhandledSuffix(text, outputType, unhandledSuffixLength);
    if (unhandledSuffixLength == 0 && pos < text.length()) {
      chunks = processTextChunk(chunks, text.substring(pos), outputType, textAcceptor);
    }
    if (chunks != null && textAcceptor instanceof ColoredChunksAcceptor) {
      ((ColoredChunksAcceptor)textAcceptor).coloredChunksAvailable(chunks);
    }
  }

  private void updateUnhandledSuffix(@NotNull String text, @NotNull Key outputType, int unhandledSuffixLength) {
    String unhandledSuffix = unhandledSuffixLength > 0 ? text.substring(text.length() - unhandledSuffixLength) : null;
    if (outputType == ProcessOutputTypes.STDOUT) {
      myUnhandledStdout = unhandledSuffix;
    }
    else if (outputType == ProcessOutputTypes.STDERR) {
      myUnhandledStderr = unhandledSuffix;
    }
  }

  @NotNull
  private String prependUnhandledText(@NotNull String text, @NotNull Key outputType) {
    String prevUnhandledText = null;
    if (outputType == ProcessOutputTypes.STDOUT) {
      prevUnhandledText = myUnhandledStdout;
      myUnhandledStdout = null;
    }
    else if (outputType == ProcessOutputTypes.STDERR) {
      prevUnhandledText = myUnhandledStderr;
      myUnhandledStderr = null;
    }
    return prevUnhandledText != null ? prevUnhandledText + text : text;
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

  /**
   * Returns the index of the first occurrence of CSI within the passed string that is greater than or equal to {@code fromIndex},
   * or negative number if CSI is not found: -1 - (length of text suffix to keep in case of an incomplete CSI).
   */
  private static int findEscSeqBeginIndex(@NotNull String text, int fromIndex) {
    int ind = text.indexOf(CSI.charAt(0), fromIndex);
    if (ind == -1) {
      return -1;
    }
    else if (ind == text.length() - 1) {
      return encodeUnhandledSuffixLength(text, ind);
    }
    return text.charAt(ind + 1) == CSI.charAt(1) ? ind : -1;
  }

  /**
   * Returns end index of all consecutive escape sequences started at {@code firstEscSeqBeginInd}, or
   * a negative number if not found. Since an escape sequence could be split among several subsequent output chunks
   * (e.g. because of automatic flushing of standard stream when its buffer is full), it should be parsed
   * when all the needed output chunks are available. To achieve that, the return value encodes
   * the length of the passed string suffix to keep in case of an incomplete last escape sequence:
   *  {@code -1 - (length of string suffix to keep in case of an incomplete last escape sequence)}.  </p>
   * If the return value is -1, no string suffix should be kept => a malformed escape sequence has been encountered.
   * If the return value is less than -1, no actual handing of the incomplete escape sequence should be performed,
   * the string suffix length should be decoded with {@code #decodeUnhandledSuffixLength(the return value)} and the suffix
   * should be preserved until the next output chunks is available.
   */
  private static int findConsecutiveEscSequencesEndIndex(@NotNull String text, int firstEscSeqBeginInd) {
    int escSeqBeginInd = firstEscSeqBeginInd;
    int lastMatchedColorEscSeqEndInd = -1;
    int escSeqEndInd;
    while ((escSeqEndInd = findEscSeqEndIndex(text, escSeqBeginInd)) >= 0) {
      if (text.charAt(escSeqEndInd) != 'm') {
        // Handle non-color escape sequences separately
        // ColoredOutputTypeRegistry expects only color escape sequences and in a single consecutive text chunk
        return lastMatchedColorEscSeqEndInd > 0 ? lastMatchedColorEscSeqEndInd : escSeqEndInd;
      }
      escSeqBeginInd = escSeqEndInd + 1;
      lastMatchedColorEscSeqEndInd = escSeqEndInd;
      if (escSeqEndInd + 1 >= text.length()) {
        return encodeUnhandledSuffixLength(text, firstEscSeqBeginInd);
      }
      if (text.charAt(escSeqEndInd + 1) != CSI.charAt(0)) {
        break;
      }
      if (escSeqEndInd + 2 >= text.length()) {
        return encodeUnhandledSuffixLength(text, firstEscSeqBeginInd);
      }
      if (text.charAt(escSeqEndInd + 2) != CSI.charAt(1)) {
        break;
      }
    }
    if (escSeqEndInd < -1) {
      return encodeUnhandledSuffixLength(text, firstEscSeqBeginInd);
    }
    return lastMatchedColorEscSeqEndInd;
  }

  private static int findEscSeqEndIndex(@NotNull String text, int escSeqBeginInd) {
    int parameterEndInd = escSeqBeginInd + CSI.length();
    while (parameterEndInd < text.length()) {
      char ch = text.charAt(parameterEndInd);
      if (Character.isDigit(ch) || ch == ';') {
        parameterEndInd++;
      }
      else {
        break;
      }
    }
    if (parameterEndInd == text.length()) {
      return encodeUnhandledSuffixLength(text, escSeqBeginInd);
    }
    return StringUtil.containsChar("ABCDEFGHJKSTfmisu", text.charAt(parameterEndInd)) ? parameterEndInd : -1;
  }

  private static int encodeUnhandledSuffixLength(@NotNull String text, int suffixStartInd) {
    return -1 - (text.length() - suffixStartInd);
  }

  private static int decodeUnhandledSuffixLength(int encodedUnhandledSuffixLength) {
    if (encodedUnhandledSuffixLength >= -1) {
      throw new AssertionError();
    }
    return -encodedUnhandledSuffixLength - 1;
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

  public interface ColoredChunksAcceptor extends ColoredTextAcceptor {
    void coloredChunksAvailable(@NotNull List<Pair<String, Key>> chunks);
  }

  @FunctionalInterface
  public interface ColoredTextAcceptor {
    void coloredTextAvailable(@NotNull String text, @NotNull Key attributes);
  }
}
