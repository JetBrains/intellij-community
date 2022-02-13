// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.charset;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class Native2AsciiCharsetEncoder extends CharsetEncoder {
  private static final char ANCHOR = Boolean.getBoolean("idea.native2ascii.lowercase") ? 'a' : 'A';
  private static final boolean DO_NOT_CONVERT_COMMENTS = Boolean.getBoolean("idea.native2ascii.skip.comments");
  private static final String LINE_SEPARATOR =
    System.getProperty("idea.native2ascii.line.separator") == null ? "\r\n" : System.getProperty("idea.native2ascii.line.separator");

  private final Charset myBaseCharset;

  Native2AsciiCharsetEncoder(Native2AsciiCharset charset) {
    super(charset, 1, 6);
    myBaseCharset = charset.getBaseCharset();
  }

  @Override
  protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
    StringBuilder currentLine = new StringBuilder();
    List<Character> characters = new ArrayList<>();
    boolean continueFlg = false;
    boolean lastIsComment = false;

    while (in.position() < in.limit()) {
      try {
        in.mark();
        char c = in.get();
        if (isLineEnd(c) || in.position() == in.limit()) {
          characters.add(c);
          Character[] array = characters.toArray(new Character[0]);
          Arrays.stream(array).forEach(character -> currentLine.append(character.charValue()));
          String str = currentLine.toString();
          if (DO_NOT_CONVERT_COMMENTS && ((str.startsWith("#") && !continueFlg) || (lastIsComment && continueFlg))) {
            ByteBuffer byteBuffer = myBaseCharset.encode(str);
            out.put(byteBuffer);
            lastIsComment = true;
          }
          else {
            CharBuffer buffer = CharBuffer.wrap(currentLine, 0, currentLine.length());
            encodeLine(buffer, out);
            lastIsComment = false;
          }
          continueFlg = str.replace(" ", "").endsWith("\\" + LINE_SEPARATOR);
          currentLine.setLength(0);
          characters.clear();
        }
        else {
          characters.add(c);
        }
      }
      catch (BufferUnderflowException e) {
        in.reset();
      }
      catch (BufferOverflowException e) {
        in.reset();
        return CoderResult.OVERFLOW;
      }
    }
    return CoderResult.UNDERFLOW;
  }

  private void encodeLine(CharBuffer in, ByteBuffer out) {
    while (in.position() < in.limit()) {
      in.mark();
      char c = in.get();
      if (c < '\u0080') {
        ByteBuffer byteBuffer = myBaseCharset.encode(Character.toString(c));
        out.put(byteBuffer);
      }
      else {
        if (out.remaining() < 6) throw new BufferOverflowException();
        out.put((byte)'\\');
        out.put((byte)'u');
        out.put(toHexChar(c >> 12));
        out.put(toHexChar((c >> 8) & 0xf));
        out.put(toHexChar((c >> 4) & 0xf));
        out.put(toHexChar(c & 0xf));
      }
    }
  }

  private static boolean isLineEnd(char curChar) {
    char c = LINE_SEPARATOR.charAt(LINE_SEPARATOR.length() - 1);
    return curChar == c;
  }

  private static byte toHexChar(int digit) {
    if (digit < 10) {
      return (byte)('0' + digit);
    }
    return (byte)(ANCHOR - 10 + digit);
  }

}
