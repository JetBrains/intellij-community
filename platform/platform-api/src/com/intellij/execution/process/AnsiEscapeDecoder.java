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
import com.intellij.util.ObjectUtils;
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

  private final ColoredOutputTypeRegistry myColoredOutputTypeRegistry = ColoredOutputTypeRegistry.getInstance();
  private String myUnhandledStdout;
  private String myUnhandledStderr;
  private ProcessOutputType myCurrentStdoutOutputType;
  private ProcessOutputType myCurrentStderrOutputType;

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
    int pos = 0, findEscSeqFromIndex = 0;
    List<Pair<String, Key>> chunks = null;
    int unhandledSuffixLength = 0;
    while (true) {
      int escSeqBeginInd = findEscSeqBeginIndex(text, findEscSeqFromIndex);
      if (escSeqBeginInd < 0) {
        if (escSeqBeginInd < -1) {
          unhandledSuffixLength = decodeUnhandledSuffixLength(escSeqBeginInd);
        }
        break;
      }
      int escSeqEndInd = findConsecutiveEscSequencesEndIndex(text, escSeqBeginInd);
      if (escSeqEndInd < 0) {
        if (escSeqEndInd == -1) {
          // malformed escape sequence => add ESC[
          findEscSeqFromIndex = escSeqBeginInd + CSI.length();
        }
        else {
          unhandledSuffixLength = decodeUnhandledSuffixLength(escSeqEndInd);
          break;
        }
      }
      else {
        assert escSeqBeginInd <= escSeqEndInd;
        if (pos < escSeqBeginInd) {
          chunks = processTextChunk(chunks, text.substring(pos, escSeqBeginInd), outputType, textAcceptor);
        }
        pos = escSeqEndInd + 1;
        findEscSeqFromIndex = pos;
        if (text.charAt(escSeqEndInd) == 'm') {
          String escSeq = text.substring(escSeqBeginInd, escSeqEndInd + 1);
          // this is a simple fix for RUBY-8996:
          // we replace several consecutive escape sequences with one which contains all these sequences
          String colorAttribute = StringUtil.replace(escSeq, M_CSI, ";");
          ProcessOutputType resultType = myColoredOutputTypeRegistry.getOutputType(colorAttribute, outputType);
          if (resultType.isStdout()) {
            myCurrentStdoutOutputType = resultType;
          }
          else if (resultType.isStderr()) {
            myCurrentStderrOutputType = resultType;
          }
        }
      }
    }
    if (pos < text.length() - unhandledSuffixLength) {
      chunks = processTextChunk(chunks, text.substring(pos, text.length() - unhandledSuffixLength), outputType, textAcceptor);
    }
    updateUnhandledSuffix(text, outputType, unhandledSuffixLength);
    if (chunks != null && textAcceptor instanceof ColoredChunksAcceptor) {
      ((ColoredChunksAcceptor)textAcceptor).coloredChunksAvailable(chunks);
    }
  }

  private void updateUnhandledSuffix(@NotNull String text, @NotNull Key outputType, int unhandledSuffixLength) {
    String unhandledSuffix = unhandledSuffixLength > 0 ? text.substring(text.length() - unhandledSuffixLength) : null;
    if (ProcessOutputType.isStdout(outputType)) {
      myUnhandledStdout = unhandledSuffix;
    }
    else if (ProcessOutputType.isStderr(outputType)) {
      myUnhandledStderr = unhandledSuffix;
    }
  }

  @NotNull
  private String prependUnhandledText(@NotNull String text, @NotNull Key outputType) {
    String prevUnhandledText = null;
    if (ProcessOutputType.isStdout(outputType)) {
      prevUnhandledText = myUnhandledStdout;
      myUnhandledStdout = null;
    }
    else if (ProcessOutputType.isStderr(outputType)) {
      prevUnhandledText = myUnhandledStderr;
      myUnhandledStderr = null;
    }
    return prevUnhandledText != null ? prevUnhandledText + text : text;
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
   * should be preserved until the next output chunk is available.
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

  /**
   * @implSpec {@code The ESC [ is followed by any number (including none) of "parameter bytes" in the
   * range 0x30–0x3F (ASCII 0–9:;<=>?), then by any number of "intermediate bytes" in the range 0x20–0x2F (ASCII space and
   * !"#$%&'()*+,-./), then finally by a single "final byte" in the range 0x40–0x7E (ASCII @A–Z[\]^_`a–z{|}~).}
   * @implNote Also, there are different sequences, <a href="http://en.wikipedia.org/wiki/ANSI_escape_code#Escape_sequences">aside CSI</a>
   */
  private static int findEscSeqEndIndex(@NotNull String text, int escSeqBeginInd) {
    int parameterEndInd = escSeqBeginInd + CSI.length();
    while (parameterEndInd < text.length()) {
      char ch = text.charAt(parameterEndInd);
      if (0x30 <= ch && ch <= 0x3F) {
        parameterEndInd++;
      }
      else {
        break;
      }
    }
    while (parameterEndInd < text.length()) {
      char ch = text.charAt(parameterEndInd);
      if (0x20 <= ch && ch <= 0x2F) {
        parameterEndInd++;
      }
      else {
        break;
      }
    }
    if (parameterEndInd == text.length()) {
      return encodeUnhandledSuffixLength(text, escSeqBeginInd);
    }
    char lastChar = text.charAt(parameterEndInd);
    return 0x40 <= lastChar && lastChar <= 0x7E ? parameterEndInd : -1;
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
    if (ProcessOutputType.isStdout(outputType)) {
      return ObjectUtils.notNull(myCurrentStdoutOutputType, outputType);
    }
    if (ProcessOutputType.isStderr(outputType)) {
      return ObjectUtils.notNull(myCurrentStderrOutputType, outputType);
    }
    return outputType;
  }

  public interface ColoredChunksAcceptor extends ColoredTextAcceptor {
    void coloredChunksAvailable(@NotNull List<Pair<String, Key>> chunks);
  }

  @FunctionalInterface
  public interface ColoredTextAcceptor {
    void coloredTextAvailable(@NotNull String text, @NotNull Key attributes);
  }
}
