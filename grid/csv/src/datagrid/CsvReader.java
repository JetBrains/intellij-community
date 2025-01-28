package com.intellij.database.datagrid;

import com.intellij.openapi.util.text.StringUtil;
import dk.brics.automaton.RunAutomaton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;

public class CsvReader {
  private long myCharacters;
  private long myLine;
  private long myLineCharacters;

  private int myLocalCount;

  private final CyclicBuffer myBuffer;

  public CsvReader(@NotNull Reader reader) {
    myBuffer = new CyclicBuffer(reader, CsvLexer.MAX_CHARACTERS*2);
  }

  public long getLine() {
    return myLine;
  }

  public long getCharacters() {
    return myCharacters;
  }

  public long getLineCharacters() {
    return myLineCharacters;
  }

  public boolean read(@NotNull String value) throws IOException {
    return read(value, false);
  }

  public boolean read(@NotNull String value, boolean eofIsOk) throws IOException {
    int count = matchInner(0, value, eofIsOk);
    if (count == -1) {
      return false;
    }
    updateCountersFromString(value, count);
    myBuffer.moveHead(count);
    return true;
  }

  private void updateCountersFromString(@NotNull String value, int charCnt) {
    myCharacters += charCnt;
    for (int i = 0, e = value.length(); i < e; ++i) {
      if (value.charAt(i) == '\n') {
        ++myLine;
        myLineCharacters = 0;
      }
      else {
        ++myLineCharacters;
      }
    }
  }

  public boolean lookAhead(@NotNull String value) throws IOException {
    return matchAhead(value, false) != -1;
  }

  public int matchAhead(@NotNull RunAutomaton automaton, int enlargeGap) throws IOException {
    int state = automaton.getInitialState();
    myLocalCount = 0;
    int lastOk = -1;
    while (myLocalCount < CsvLexer.MAX_CHARACTERS && (lastOk == -1 || myLocalCount - lastOk < enlargeGap)) {
      myBuffer.ensureAvailable(myLocalCount + 2);
      int c = getCharNormalized(myLocalCount, 1);
      state = automaton.step(state, c == -1 ? 0 : (char)c);
      if (state == -1) return lastOk;
      if (automaton.isAccept(state)) {
        lastOk = myLocalCount;
        continue;
      }
      if (c == -1) return lastOk;
    }
    return lastOk;
  }

  private int getCharNormalized(int idx, int dir) {
    int c = myBuffer.charAhead(idx);
    if (c == -1) return -1;
    ++myLocalCount;
    char ac = c == '\n' ? '\r' : c == '\r' ? '\n' : 0;
    if (ac != 0 && (dir > 0 || idx > 0)) {
      c = myBuffer.charAhead(idx + dir);
      if (c == ac) ++myLocalCount;
      c = '\n';
    }
    return c;
  }

  public int matchAhead(@NotNull String value, boolean eofIsOk) throws IOException {
    return matchAhead(0, value, eofIsOk);
  }
  public int matchAhead(int start, @NotNull String value, boolean eofIsOk) throws IOException {
    if (value.isEmpty()) return -1;
    return matchInner(start, value, eofIsOk);
  }

  private int matchInner(int start, @NotNull String value, boolean eofIsOk) throws IOException {
    if (value.isEmpty()) return -1;
    myBuffer.ensureAvailable(start + value.length() * 2);
    myLocalCount = 0;
    for (int i = 0; i < value.length(); ++i) {
      int c = getCharNormalized(start + myLocalCount, 1);
      if (c == -1) {
        return eofIsOk ? myLocalCount : -1;
      }
      if (value.charAt(i) != c) {
        return -1;
      }
    }
    return myLocalCount;
  }

  public int matchBackward(int start, @NotNull String value) throws IOException {
    if (value.isEmpty() || value.length() > start) return -1;
    myBuffer.ensureAvailable(start + 1);
    myLocalCount = 0;
    for (int i = value.length() - 1; i >= 0; --i) {
      if (myLocalCount > start) return -1;
      int c = getCharNormalized(start - myLocalCount - 1, -1);
      if (c == -1 || value.charAt(i) != c) {
        return -1;
      }
    }
    return myLocalCount;
  }

  public int matchBackward(int start, @NotNull RunAutomaton automaton, int maxCount) throws IOException {
    myBuffer.ensureAvailable(start + 1);
    int state = automaton.getInitialState();
    myLocalCount = 0;
    int lastOk = -1;
    if (myBuffer.charAhead(start) == -1) {
      state = automaton.step(state, '\0');
      if (start == -1) return -1;
      if (automaton.isAccept(state)) lastOk = 0;
    }
    while (myLocalCount < start && myLocalCount < maxCount) {
      int c = getCharNormalized(start - myLocalCount - 1, -1);
      if (c == '\0') return -1; //do not rewind '\0' in the stream to overcome infinite looping
      state = automaton.step(state, (char)c);
      if (state == -1) return lastOk;
      if (automaton.isAccept(state)) {
        lastOk = myLocalCount;
        continue;
      }
      if (c == -1) return lastOk;
    }
    return lastOk;
  }



  public boolean isReady() throws IOException {
    myBuffer.ensureAvailable(1);
    int c = myBuffer.charAhead(0);
    return c != -1;
  }


  public @Nullable String readString(int count) throws IOException {
    myBuffer.ensureAvailable(count * 2);
    StringBuilder sb = new StringBuilder(count);
    myLocalCount = 0;
    while (myLocalCount < count) {
      int c = getCharNormalized(myLocalCount, 1);
      if (c == -1) break;
      sb.append((char)c);
    }
    if (sb.isEmpty()) return null;
    myBuffer.moveHead(myLocalCount);
    String result = sb.toString();
    updateCountersFromString(result, myLocalCount);
    return result;
  }

  public @Nullable String readAhead(int count) throws IOException {
    if (count == 0) return null;
    myBuffer.ensureAvailable(count);
    String result = myBuffer.subStringAhead(0, Math.min(count, myBuffer.getAvailable()));
    return StringUtil.convertLineSeparators(result);
  }

  public void close() throws IOException {
    myBuffer.close();
  }

  private static class CyclicBuffer {
    private final Reader myReader;
    private final char[] myBuffer;
    private int myHead = 0;
    private int myAvailable = 0;
    private boolean myEof = false;

    private CyclicBuffer(@NotNull Reader reader, int bufLen) {
      myReader = reader;
      myBuffer = new char[bufLen];
    }

    private void moveHead(int n) {
      int n2 = Math.min(n, myAvailable);
      myHead = cyc(myHead + n2);
      myAvailable -= n2;
    }

    private int charAhead(int i) {
      if (i < myAvailable) {
        return myBuffer[cyc(myHead + i)];
      }
      if (myEof) {
        return -1;
      }
      throw new BufferUnderflowException();
    }

    private int cyc(int n) {
      return (myBuffer.length + n) % myBuffer.length;
    }

    private void ensureAvailable(int required) throws IOException {
      if (myEof) return;
      if (myAvailable >= required) return;
      int toRead = required - myAvailable;
      readMore(toRead, Math.max(toRead, (myBuffer.length - myAvailable) * 3 / 4));
    }

    private void readMore(int amount, int recommended) throws IOException {
      if (recommended + myAvailable >= myBuffer.length) {
        throw new BufferOverflowException();
      }
      int cnt = 0;
      while (!myEof) {
        cnt += readMore(recommended - cnt);
        if (cnt >= amount) break;
      }
    }
    private int readMore(int amount) throws IOException {
      int tail = cyc(myHead + myAvailable);
      int strand = Math.min(amount, myBuffer.length - tail);
      int read = myReader.read(myBuffer, tail, strand);
      if (read == -1) {
        myEof = true;
        return 0;
      }
      myAvailable += read;
      if (read == strand && strand < amount) {
        return read + readMore(amount - strand);
      }
      return read;
    }

    public String subStringAhead(int from, int len) {
      if (from + len > myAvailable) throw new BufferUnderflowException();
      int h = cyc(myHead + from);
      if (h + len <= myBuffer.length) return new String(myBuffer, h, len);
      char[] chars = new char[len];
      int len1 = myBuffer.length - h;
      System.arraycopy(myBuffer, h, chars, 0, len1);
      System.arraycopy(myBuffer, 0, chars, len1, len - len1);
      return new String(chars);
    }

    public int getAvailable() {
      return myAvailable;
    }

    public void close() throws IOException {
      myReader.close();
    }
  }
}
