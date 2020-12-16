// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

final class DirectByteBufferBackedInputStream extends InputStream {
  private ByteBuffer buffer;

  DirectByteBufferBackedInputStream(ByteBuffer buffer) {
    this.buffer = buffer;
  }

  @Override
  public int read() throws IOException {
    return buffer.hasRemaining() ? buffer.get() & 0xff : -1;
  }

  @Override
  public int read(byte[] bytes, int offset, int length)
    throws IOException {
    if (!buffer.hasRemaining()) {
      return -1;
    }

    int actualLength = Math.min(length, buffer.remaining());
    buffer.get(bytes, offset, actualLength);
    return actualLength;
  }

  @Override
  public byte[] readNBytes(int length) {
    byte[] result = new byte[Math.min(length, buffer.remaining())];
    buffer.get(result);
    return result;
  }

  @Override
  public int readNBytes(byte[] bytes, int offset, int length) {
    int actualLength = Math.min(length, buffer.remaining());
    buffer.get(bytes, offset, actualLength);
    return actualLength;
  }

  @Override
  public int available() throws IOException {
    return buffer.remaining();
  }

  @Override
  public byte[] readAllBytes() throws IOException {
    byte[] result = new byte[buffer.remaining()];
    buffer.get(result);
    return result;
  }

  @Override
  public long skip(long length) throws IOException {
    int actualLength = Math.min((int)length, buffer.remaining());
    buffer.position(buffer.position() + actualLength);
    return actualLength;
  }

  @Override
  public void close() {
    ByteBuffer buffer = this.buffer;
    if (buffer == null) {
      // already closed
      return;
    }

    this.buffer = null;
    DirectByteBufferPool.DEFAULT_POOL.release(buffer);
  }
}
