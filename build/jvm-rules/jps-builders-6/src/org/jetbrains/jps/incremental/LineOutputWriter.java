// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.Writer;
import java.nio.CharBuffer;

@ApiStatus.Internal
public abstract class LineOutputWriter extends Writer {
  private final LineParser myLineParser = new LineParser();

  @Override
  public void write(int c) {
    processData(new CharSequenceIterator(c));
  }

  @Override
  public void write(char[] cbuf) {
    processData(new CharSequenceIterator(cbuf));
  }

  @Override
  public void write(String str) {
    processData(new CharSequenceIterator(str));
  }

  @Override
  public void write(String str, int off, int len) {
    processData(new CharSequenceIterator(str.subSequence(off, off + len)));
  }

  @Override
  public Writer append(CharSequence csq) {
    processData(new CharSequenceIterator(csq));
    return this;
  }

  @Override
  public Writer append(CharSequence csq, int start, int end) {
    processData(new CharSequenceIterator(csq.subSequence(start, end)));
    return this;
  }

  @Override
  public Writer append(char c) {
    processData(new CharSequenceIterator(c));
    return this;
  }

  @Override
  public void write(char[] cbuf, int off, int len) {
    processData(new CharSequenceIterator(cbuf, off, len));
  }

  private void processData(CharIterator data) {
    while (myLineParser.parse(data)) {
      final String line = myLineParser.getResult();
      myLineParser.reset();
      lineAvailable(line);
    }
  }


  @Override
  public void flush() throws IOException {
  }

  @Override
  public void close() throws IOException {
    try {
      if (myLineParser.hasData()) {
        lineAvailable(myLineParser.getResult());
      }
    }
    finally {
      myLineParser.reset();
    }
  }

  protected abstract void lineAvailable(String line);

  private interface CharIterator {
    char nextChar();
    boolean hasData();
  }

  private static final class LineParser {
    private final StringBuilder myData = new StringBuilder();
    private boolean myFoundCR = false;

    public boolean parse(CharIterator it) {
      while (it.hasData()) {
        final char ch = it.nextChar();
        if (ch == '\r') {
          myFoundCR = true;
        }
        else if (ch == '\n') {
          myFoundCR = false;
          return true;
        }
        else {
          if (myFoundCR) {
            myData.append('\r');
            myFoundCR = false;
          }
          myData.append(ch);
        }
      }
      return false;
    }

    public boolean hasData() {
      return myData.length() > 0;
    }

    public String getResult() {
      return myData.toString();
    }

    public void reset() {
      myFoundCR = false;
      myData.setLength(0);
    }
  }

  private static final class CharSequenceIterator implements CharIterator {
    private final CharSequence myChars;
    private int myCursor = 0;

    CharSequenceIterator(final int ch) {
      this((char)ch);
    }

    CharSequenceIterator(final char ch) {
      this(new SingleCharSequence(ch));
    }

    CharSequenceIterator(char[] chars) {
      this(chars, 0, chars.length);
    }

    CharSequenceIterator(final char[] chars, final int offset, final int length) {
      this(CharBuffer.wrap(chars, offset, length));
    }

    CharSequenceIterator(CharSequence sequence) {
      myChars = sequence;
    }

    @Override
    public char nextChar() {
      return myChars.charAt(myCursor++);
    }

    @Override
    public boolean hasData() {
      return myCursor < myChars.length();
    }
  }
}
