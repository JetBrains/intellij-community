/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;

/**
 * @author Eugene Zhuravlev
 */
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

  private static class LineParser {
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

  private static class CharSequenceIterator implements CharIterator {
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
      this(new CharArrayCharSequence(chars, offset, offset+length));
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

  private  static class CharArrayCharSequence implements CharSequence {
    protected final char[] myChars;
    protected final int myStart;
    protected final int myEnd;

    public CharArrayCharSequence(@NotNull char[] chars, int start, int end) {
      if (start < 0 || end > chars.length || start > end) {
        throw new IndexOutOfBoundsException("chars.length:" + chars.length + ", start:" + start + ", end:" + end);
      }
      myChars = chars;
      myStart = start;
      myEnd = end;
    }

    @Override
    public final int length() {
      return myEnd - myStart;
    }

    @Override
    public final char charAt(int index) {
      return myChars[index + myStart];
    }

    @NotNull
    @Override
    public CharSequence subSequence(int start, int end) {
      return start == 0 && end == length() ? this : new CharArrayCharSequence(myChars, myStart + start, myStart + end);
    }

    @Override
    @NotNull
    public String toString() {
      return new String(myChars, myStart, myEnd - myStart);
    }
  }

  private static final class SingleCharSequence implements CharSequence {
    private final char myCh;

    public SingleCharSequence(char ch) {
      myCh = ch;
    }

    public int length() {
      return 1;
    }

    public char charAt(int index) {
      if (index != 0) {
        throw new IndexOutOfBoundsException("Index out of bounds: " + index);
      }
      return myCh;
    }

    public CharSequence subSequence(int start, int end) {
      throw new RuntimeException("Method subSequence not implemented");
    }

    public String toString() {
      return String.valueOf(myCh);
    }
  }
}
