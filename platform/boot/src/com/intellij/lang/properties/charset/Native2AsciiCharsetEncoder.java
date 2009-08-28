package com.intellij.lang.properties.charset;

/**
 * @author Alexey
 */

import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.Charset;
import java.nio.CharBuffer;
import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.nio.BufferOverflowException;

class Native2AsciiCharsetEncoder extends CharsetEncoder {
  private final Charset myBaseCharset;

  public Native2AsciiCharsetEncoder(Native2AsciiCharset charset) {
    super(charset, 1, 6);
    myBaseCharset = charset.getBaseCharset();
  }

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
          out.put((byte)Character.forDigit(c >> 12, 16));
          out.put((byte)Character.forDigit((c >> 8) & 0xf, 16));
          out.put((byte)Character.forDigit((c >> 4) & 0xf, 16));
          out.put((byte)Character.forDigit(c & 0xf, 16));
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
}