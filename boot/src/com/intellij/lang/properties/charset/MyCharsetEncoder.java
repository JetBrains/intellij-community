package com.intellij.lang.properties.charset;

/**
 * @author Alexey
 */

import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.CharBuffer;
import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.nio.BufferOverflowException;

class MyCharsetEncoder extends CharsetEncoder {
  public MyCharsetEncoder() {
    super(AsciiToNativeCharset.INSTANCE, 1, 6);
  }

  protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
    while (in.position() < in.limit()) {
      in.mark();
      try {
        char c = in.get();
        if (c < 255) {
          out.put((byte)c);
        }
        else {
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