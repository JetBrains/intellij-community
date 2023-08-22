// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.psi.impl;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.IntStream;

public abstract class JSStringLiteralEscaper<T extends PsiLanguageInjectionHost> extends LiteralTextEscaper<T> {
  /**
   * Offset in injected string -> offset in host string
   * Last element contains imaginary offset for the character after the last one in injected string. It would be host string length.
   * E.g. for "aa\nbb" it is [0,1,2,4,5,6]
   */
  private int[] outSourceOffsets;

  public JSStringLiteralEscaper(T host) {
    super(host);
  }

  @Override
  public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    String subText = rangeInsideHost.substring(myHost.getText());

    Ref<int[]> sourceOffsetsRef = new Ref<>();
    boolean result = parseStringCharacters(subText, outChars, sourceOffsetsRef, isRegExpLiteral(), !isOneLine());
    outSourceOffsets = sourceOffsetsRef.get();
    return result;
  }

  protected abstract boolean isRegExpLiteral();

  @Override
  public int getOffsetInHost(int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    int result = offsetInDecoded < outSourceOffsets.length ? outSourceOffsets[offsetInDecoded] : -1;
    if (result == -1) return -1;
    return Math.min(result, rangeInsideHost.getLength()) + rangeInsideHost.getStartOffset();
  }

  @Override
  public boolean isOneLine() {
    return true;
  }

  public static boolean parseStringCharacters(String chars, StringBuilder outChars, Ref<int[]> sourceOffsetsRef, boolean regExp, boolean escapeBacktick) {
    if (chars.indexOf('\\') < 0) {
      outChars.append(chars);
      sourceOffsetsRef.set(IntStream.range(0, chars.length() + 1).toArray());
      return true;
    }

    int[] sourceOffsets = new int[chars.length() + 1];
    int index = 0;
    final int outOffset = outChars.length();
    boolean result = true;
    loop:
    while (index < chars.length()) {
      char c = chars.charAt(index++);

      sourceOffsets[outChars.length() - outOffset] = index - 1;
      sourceOffsets[outChars.length() + 1 - outOffset] = index;

      if (c != '\\') {
        outChars.append(c);
        continue;
      }
      if (index == chars.length()) {
        result = false;
        break;
      }
      c = chars.charAt(index++);
      if (escapeBacktick && c == '`') {
        outChars.append(c);
      }
      else if (regExp) {
        if (c != '/') {
          outChars.append('\\');
        }
        outChars.append(c);
      }
      else {
        switch (c) {
          case 'b' -> outChars.append('\b');
          case 't' -> outChars.append('\t');
          case 'n', '\n' -> outChars.append('\n');
          case 'f' -> outChars.append('\f');
          case 'r' -> outChars.append('\r');
          case '"' -> outChars.append('"');
          case '/' -> outChars.append('/');
          case '\'' -> outChars.append('\'');
          case '\\' -> outChars.append('\\');
          case '0', '1', '2', '3', '4', '5', '6', '7' -> {
            char startC = c;
            int v = (int)c - '0';
            if (index < chars.length()) {
              c = chars.charAt(index++);
              if ('0' <= c && c <= '7') {
                v <<= 3;
                v += c - '0';
                if (startC <= '3' && index < chars.length()) {
                  c = chars.charAt(index++);
                  if ('0' <= c && c <= '7') {
                    v <<= 3;
                    v += c - '0';
                  }
                  else {
                    index--;
                  }
                }
              }
              else {
                index--;
              }
            }
            outChars.append((char)v);
          }
          case 'x' -> {
            if (index + 2 <= chars.length()) {
              try {
                int v = Integer.parseInt(chars.substring(index, index + 2), 16);
                outChars.append((char)v);
                index += 2;
              }
              catch (Exception e) {
                result = false;
                break loop;
              }
            }
            else {
              result = false;
              break loop;
            }
          }
          case 'u' -> {
            if (index + 3 <= chars.length() && chars.charAt(index) == '{') {
              int end = chars.indexOf('}', index + 1);
              if (end < 0) {
                result = false;
                break loop;
              }
              try {
                int v = Integer.parseInt(chars.substring(index + 1, end), 16);
                c = chars.charAt(index + 1);
                if (c == '+' || c == '-') {
                  result = false;
                  break loop;
                }
                outChars.appendCodePoint(v);
                index = end + 1;
              }
              catch (Exception e) {
                result = false;
                break loop;
              }
            }
            else if (index + 4 <= chars.length()) {
              try {
                int v = Integer.parseInt(chars.substring(index, index + 4), 16);
                c = chars.charAt(index);
                if (c == '+' || c == '-') {
                  result = false;
                  break loop;
                }
                outChars.append((char)v);
                index += 4;
              }
              catch (Exception e) {
                result = false;
                break loop;
              }
            }
            else {
              result = false;
              break loop;
            }
          }
          default -> outChars.append(c);
        }
      }

      sourceOffsets[outChars.length() - outOffset] = index;
    }

    sourceOffsets[outChars.length() - outOffset] = chars.length();

    sourceOffsetsRef.set(Arrays.copyOf(sourceOffsets, outChars.length() - outOffset + 1));
    return result;
  }
}
