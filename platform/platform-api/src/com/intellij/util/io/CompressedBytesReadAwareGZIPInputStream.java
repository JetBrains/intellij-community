/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

public class CompressedBytesReadAwareGZIPInputStream extends GZIPInputStream {
  private final BytesReadAwareInputStream myInputStream;

  private CompressedBytesReadAwareGZIPInputStream(@NotNull BytesReadAwareInputStream inputStream) throws IOException {
    super(inputStream);
    myInputStream = inputStream;
  }

  public long getCompressedBytesRead() {
    return myInputStream.myBytesRead.get();
  }

  @NotNull
  public static CompressedBytesReadAwareGZIPInputStream create(@NotNull InputStream inputStream) throws IOException {
    return new CompressedBytesReadAwareGZIPInputStream(new BytesReadAwareInputStream(inputStream));
  }

  private static class BytesReadAwareInputStream extends InputStream {
    private final InputStream myInputStream;
    private final AtomicLong myBytesRead = new AtomicLong(0);

    public BytesReadAwareInputStream(@NotNull InputStream inputStream) {
      myInputStream = inputStream;
    }

    public int read() throws IOException {
      long bytesReadBefore = myBytesRead.get();
      int data = myInputStream.read();
      myBytesRead.compareAndSet(bytesReadBefore, bytesReadBefore + 1);
      return data;
    }

    @Override
    public int read(@NotNull byte[] b) throws IOException {
      long bytesReadBefore = myBytesRead.get();
      int bytesRead = myInputStream.read(b);
      myBytesRead.compareAndSet(bytesReadBefore, bytesReadBefore + bytesRead);
      return bytesRead;
    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
      long bytesReadBefore = myBytesRead.get();
      int bytesRead = myInputStream.read(b, off, len);
      myBytesRead.compareAndSet(bytesReadBefore, bytesReadBefore + bytesRead);
      return bytesRead;
    }

    public long skip(long n) throws IOException {
      long bytesReadBefore = myBytesRead.get();
      long bytesSkipped = myInputStream.skip(n);
      myBytesRead.compareAndSet(bytesReadBefore, bytesReadBefore + bytesSkipped);
      return bytesSkipped;
    }

    public int available() throws IOException {
      return myInputStream.available();
    }

    public void close() throws IOException {
      myInputStream.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
      myInputStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
      myInputStream.reset();
    }

    @Override
    public boolean markSupported() {
      return myInputStream.markSupported();
    }
  }
}
