package com.intellij.json.psi.impl;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

public abstract class JSStringLiteralEscaper<T extends PsiLanguageInjectionHost> extends LiteralTextEscaper<T> {
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
    return (result <= rangeInsideHost.getLength() ? result : rangeInsideHost.getLength()) + rangeInsideHost.getStartOffset();
  }

  @Override
  public boolean isOneLine() {
    return true;
  }

  public static boolean parseStringCharacters(String chars, StringBuilder outChars, Ref<int[]> sourceOffsetsRef, boolean regExp, boolean escapeBacktick) {
    int[] sourceOffsets = new int[chars.length() + 1];
    sourceOffsetsRef.set(sourceOffsets);

    if (chars.indexOf('\\') < 0) {
      outChars.append(chars);
      for (int i = 0; i < sourceOffsets.length; i++) {
        sourceOffsets[i] = i;
      }
      return true;
    }

    int index = 0;
    final int outOffset = outChars.length();
    while (index < chars.length()) {
      char c = chars.charAt(index++);

      sourceOffsets[outChars.length() - outOffset] = index - 1;
      sourceOffsets[outChars.length() + 1 - outOffset] = index;

      if (c != '\\') {
        outChars.append(c);
        continue;
      }
      if (index == chars.length()) return false;
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
          case 'b':
            outChars.append('\b');
            break;

          case 't':
            outChars.append('\t');
            break;

          case 'n':
            outChars.append('\n');
            break;

          case 'f':
            outChars.append('\f');
            break;

          case 'r':
            outChars.append('\r');
            break;

          case '"':
            outChars.append('"');
            break;

          case '/':
            outChars.append('/');
            break;

          case '\n':
            outChars.append('\n');
            break;
          case '\'':
            outChars.append('\'');
            break;

          case '\\':
            outChars.append('\\');
            break;

          case '0':
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7': {
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
          break;
          case 'x':
            if (index + 2 <= chars.length()) {
              try {
                int v = Integer.parseInt(chars.substring(index, index + 2), 16);
                outChars.append((char)v);
                index += 2;
              }
              catch (Exception e) {
                return false;
              }
            }
            else {
              return false;
            }
            break;
          case 'u':
            if (index + 4 <= chars.length()) {
              try {
                int v = Integer.parseInt(chars.substring(index, index + 4), 16);
                //line separators are invalid here
                if (v == 0x000a || v == 0x000d) return false;
                c = chars.charAt(index);
                if (c == '+' || c == '-') return false;
                outChars.append((char)v);
                index += 4;
              }
              catch (Exception e) {
                return false;
              }
            }
            else {
              return false;
            }
            break;

          default:
            outChars.append(c);
            break;
        }
      }

      sourceOffsets[outChars.length() - outOffset] = index;
    }
    return true;
  }
}
