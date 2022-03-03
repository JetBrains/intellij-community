// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.charset;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

final class Native2AsciiCharsetEncoder extends CharsetEncoder {
  private static final char ANCHOR = Boolean.getBoolean("idea.native2ascii.lowercase") ? 'a' : 'A';
  
  private final Charset myBaseCharset;

  Native2AsciiCharsetEncoder(Native2AsciiCharset charset) {
    super(charset, 1, 6);
    myBaseCharset = charset.getBaseCharset();
  }

  @Override
  protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
    while (in.position() < in.limit()) {
      in.mark();
      try {
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

  private static byte toHexChar(int digit) {
    if (digit < 10) {
      return (byte)('0' + digit);
    }
    return (byte)(ANCHOR - 10 + digit);
  }
}
