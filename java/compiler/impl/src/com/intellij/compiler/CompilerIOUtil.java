// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class CompilerIOUtil {
  private final static ThreadLocal<byte[]> myBuffer = ThreadLocal.withInitial(() -> new byte[1024]);

  private CompilerIOUtil() {}

  public static String readString(DataInput stream) throws IOException {
    final int length = stream.readInt();
    if (length == -1) {
      return null;
    }

    if (length == 0) {
      return "";
    }

    char[] chars = new char[length];
    int charsRead = 0;

    final byte[] buff = myBuffer.get();
    while (charsRead < length) {
      final int bytesRead = Math.min((length - charsRead) * 2, buff.length);
      stream.readFully(buff, 0, bytesRead);
      for (int i = 0 ; i < bytesRead; i += 2) {
        chars[charsRead++] = (char)((buff[i] << 8) + (buff[i + 1] & 0xFF));
      }
    }

    return new String(chars);
  }

  public static void writeString(String s, DataOutput stream) throws IOException {
    if (s == null) {
      stream.writeInt(-1);
      return;
    }

    final int len = s.length();
    stream.writeInt(len);
    if (len == 0) {
      return;
    }

    int charsWritten = 0;
    final byte[] buff = myBuffer.get();
    while (charsWritten < len) {
      final int bytesWritten = Math.min((len - charsWritten) * 2, buff.length);
      for (int i = 0; i < bytesWritten; i += 2) {
        char aChar = s.charAt(charsWritten++);
        buff[i] = (byte)((aChar >>> 8) & 0xFF);
        buff[i + 1] = (byte)((aChar) & 0xFF);
      }
      stream.write(buff, 0, bytesWritten);
    }
  }
}
