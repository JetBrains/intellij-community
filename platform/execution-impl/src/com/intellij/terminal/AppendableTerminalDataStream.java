// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.jediterm.terminal.TerminalDataStream;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingDeque;

public class AppendableTerminalDataStream implements TerminalDataStream, Appendable {
  private final LinkedBlockingDeque<Character> myQueue = new LinkedBlockingDeque<>(10000000);
  private final LinkedBlockingDeque<Character> myPushBackQueue = new LinkedBlockingDeque<>();

  @Override
  public char getChar() throws IOException {
    Character ch = myPushBackQueue.poll();
    if (ch != null) {
      return ch;
    }
    try {
      return myQueue.take();
    }
    catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void pushChar(char c) {
    myPushBackQueue.addFirst(c);
  }

  @Override
  public String readNonControlCharacters(int maxLength) {
    StringBuilder sb = new StringBuilder();
    while (sb.length() < maxLength) {
      Character c = myPushBackQueue.peek();
      if (c != null) {
        if (c.charValue() < 32) {
          break;
        }
        sb.append(myPushBackQueue.poll());
      }
      else {
        c = myQueue.peek();
        if (c == null || c.charValue() < 32) {
          break;
        }
        sb.append(myQueue.poll());
      }
    }

    return sb.toString();
  }

  @Override
  public void pushBackBuffer(char[] chars, int length) {
    for (int i = length - 1; i >= 0; i--) {
      myPushBackQueue.addFirst(chars[i]);
    }
  }

  @Override
  public boolean isEmpty() {
    return myPushBackQueue.isEmpty() && myQueue.isEmpty();
  }

  @Override
  public Appendable append(CharSequence csq) throws IOException {
    return append(csq, 0, csq.length());
  }

  @Override
  public Appendable append(CharSequence csq, int start, int end) throws IOException {
    for (int i = start; i < end; i++) {
      append(csq.charAt(i));
    }
    return this;
  }

  @Override
  public Appendable append(char c) throws IOException {
    try {
      myQueue.put(c);
    }
    catch (InterruptedException e) {
      throw new IOException(e);
    }
    return this;
  }
}
