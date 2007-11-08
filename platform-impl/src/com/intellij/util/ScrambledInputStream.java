package com.intellij.util;

import java.io.IOException;
import java.io.InputStream;

public class ScrambledInputStream extends InputStream{
  private static final int MASK = ScrambledOutputStream.MASK;
  private InputStream myOriginalStream;

  public ScrambledInputStream(InputStream originalStream) {
    myOriginalStream = originalStream;
  }

  public int read() throws IOException {
    int b = myOriginalStream.read();
    if (b == -1) return -1;
    return b ^ MASK;
  }

  public int read(byte[] b, int off, int len) throws IOException {
    int read = myOriginalStream.read(b, off, len);
    for(int i = 0; i < read; i++){
      b[off + i] ^= MASK;
    }
    return read;
  }

  public long skip(long n) throws IOException {
    return myOriginalStream.skip(n);
  }

  public int available() throws IOException {
    return myOriginalStream.available();
  }

  public void close() throws IOException {
    myOriginalStream.close();
  }

  public synchronized void mark(int readlimit) {
    myOriginalStream.mark(readlimit);
  }

  public synchronized void reset() throws IOException {
    myOriginalStream.reset();
  }

  public boolean markSupported() {
    return myOriginalStream.markSupported();
  }
}
