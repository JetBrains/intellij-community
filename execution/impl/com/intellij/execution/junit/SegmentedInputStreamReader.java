/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.junit;

import com.intellij.execution.junit2.SegmentedInputStream;

import java.io.IOException;
import java.io.Reader;

/**
 * @author Eugene Zhuravlev
*         Date: Apr 25, 2007
*/
public class SegmentedInputStreamReader extends Reader {
  private final SegmentedInputStream myStream;

  public SegmentedInputStreamReader(SegmentedInputStream stream) {
    myStream = stream;
  }

  public void close() throws IOException {
    myStream.close();
  }

  public boolean ready() throws IOException {
    return myStream.available() > 0;
  }

  public int read(final char[] cbuf, final int off, final int len) throws IOException {
    for (int i = 0; i < len; i++) {
      final int aChar = myStream.read();
      if (aChar == -1) return i == 0 ? -1 : i;
      cbuf[off + i] = (char)aChar;
    }
    return len;
  }
}
