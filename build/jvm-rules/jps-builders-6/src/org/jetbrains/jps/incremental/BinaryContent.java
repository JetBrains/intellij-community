// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public final class BinaryContent {
  private final byte[] myBuffer;
  private final int myOffset;
  private final int myLength;

  public BinaryContent(byte[] buf) {
    this(buf, 0, buf.length);
  }

  public BinaryContent(byte[] buf, int off, int len) {
    myBuffer = buf;
    myOffset = off;
    myLength = len;
  }

  public byte[] getBuffer() {
    return myBuffer;
  }

  public int getOffset() {
    return myOffset;
  }

  public int getLength() {
    return myLength;
  }

  public byte[] toByteArray() {
    return Arrays.copyOfRange(myBuffer, myOffset, myOffset + myLength);
  }

  public void saveToFile(File file) throws IOException {
    try {
      _writeToFile(file, this);
    }
    catch (IOException e) {
      // assuming the reason is non-existing parent
      final File parentFile = file.getParentFile();
      if (parentFile == null) {
        throw e;
      }
      //noinspection ResultOfMethodCallIgnored
      parentFile.mkdirs();
      // second attempt
      _writeToFile(file, this);
    }
  }

  private static void _writeToFile(final File file, BinaryContent content) throws IOException {
    try (OutputStream stream = new FileOutputStream(file)) {
      stream.write(content.getBuffer(), content.getOffset(), content.getLength());
    }
  }

}
