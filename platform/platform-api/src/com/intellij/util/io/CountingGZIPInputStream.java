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
import java.util.zip.GZIPInputStream;

/**
 * A stream for reading compressed data in the GZIP file format.
 * Total amount of compressed read bytes can be accessed via {@link #getCompressedBytesRead()}.
 *
 * Note that this implementation is not thread safe.
 */
public class CountingGZIPInputStream extends GZIPInputStream {
  private final CountingInputStream myInputStream;

  private CountingGZIPInputStream(@NotNull CountingInputStream inputStream) throws IOException {
    super(inputStream);
    myInputStream = inputStream;
  }

  public long getCompressedBytesRead() {
    return myInputStream.myBytesRead;
  }

  @NotNull
  public static CountingGZIPInputStream create(@NotNull InputStream inputStream) throws IOException {
    return new CountingGZIPInputStream(new CountingInputStream(inputStream));
  }

  private static class CountingInputStream extends InputStream {
    private final InputStream myInputStream;
    private long myBytesRead = 0;

    public CountingInputStream(@NotNull InputStream inputStream) {
      myInputStream = inputStream;
    }

    public int read() throws IOException {
      int data = myInputStream.read();
      myBytesRead++;
      return data;
    }

    @Override
    public int read(@NotNull byte[] b) throws IOException {
      int bytesRead = myInputStream.read(b);
      myBytesRead += bytesRead;
      return bytesRead;
    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
      int bytesRead = myInputStream.read(b, off, len);
      myBytesRead += bytesRead;
      return bytesRead;
    }

    public long skip(long n) throws IOException {
      long bytesSkipped = myInputStream.skip(n);
      myBytesRead += bytesSkipped;
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
