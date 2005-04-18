package com.intellij.lang.properties.charset;

/**
 * @author Alexey
 */

import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.BufferUnderflowException;
import java.nio.BufferOverflowException;

class MyCharsetDecoder extends CharsetDecoder {
  private static final char INVALID_CHAR = (char)-1;

  public MyCharsetDecoder() {
    super(AsciiToNativeCharset.INSTANCE, 1, 6);
  }

  protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
    byte[] ord = new byte[4];
    try {
      while (in.position() < in.limit()) {
        in.mark();
        byte b = in.get();
        if (b == '\\') {
          byte next = in.get();
          if (next == 'u') {
            ord[0] = in.get();
            ord[1] = in.get();
            ord[2] = in.get();
            ord[3] = in.get();
            char decoded = unicode(ord);
            if (decoded == INVALID_CHAR) {
              in.reset();
              return CoderResult.unmappableForLength(6);
            }
            else {
              out.put(decoded);
            }
          }
          else {
            out.put('\\');
            out.put((char)next);
          }
        }
        else {
          out.put((char)b);
        }
      }
    }
    catch (BufferUnderflowException e) {
      in.reset();
    }
    catch (BufferOverflowException e) {
      in.reset();
      return CoderResult.OVERFLOW;
    }
    return CoderResult.UNDERFLOW;
  }

  private static char unicode(byte[] ord) {
    int d1 = Character.digit((char)ord[0], 16);
    if (d1 == -1) return INVALID_CHAR;
    int d2 = Character.digit((char)ord[1], 16);
    if (d2 == -1) return INVALID_CHAR;
    int d3 = Character.digit((char)ord[2], 16);
    if (d3 == -1) return INVALID_CHAR;
    int d4 = Character.digit((char)ord[3], 16);
    if (d4 == -1) return INVALID_CHAR;
    int b1 = (d1 << 12) & 0xF000;
    int b2 = (d2 << 8) & 0x0F00;
    int b3 = (d3 << 4) & 0x00F0;
    int b4 = (d4 << 0) & 0x000F;
    return (char)(b1 | b2 | b3 | b4);
  }

}