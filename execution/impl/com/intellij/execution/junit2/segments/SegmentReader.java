package com.intellij.execution.junit2.segments;

import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;

public class SegmentReader {
  private final String myString;
  private final char[] myChars;
  private int myPosition = 0;

  public SegmentReader(final String packet) {
    myString = packet;
    myChars = packet.toCharArray();
  }

  public String upTo(final char symbol) {
    int position = myPosition;
    while (position < myChars.length && myChars[position] != symbol) position++;
    final String result = advanceTo(position);
    skip(1);
    return result;
  }

  public void skip(final int count) {
    myPosition = Math.min(myChars.length, myPosition + count);
  }

  public String upToEnd() {
    return advanceTo(myChars.length);
  }

  private String advanceTo(final int position) {
    final String result = myString.substring(myPosition, position);
    myPosition = position;
    return result;
  }

  public String readLimitedString() {
    final int symbolCount = readInt();
    return advanceTo(myPosition + symbolCount);
  }

  public int readInt() {
    final String intString = upTo(PoolOfDelimiters.INTEGER_DELIMITER);
    return Integer.parseInt(intString);
  }

  public char readChar() {
    myPosition++;
    return myChars[myPosition - 1];
  }

  public boolean isAtEnd() {
    return myPosition == myChars.length;
  }

  public String[] readStringArray() {
    final int count = readInt();
    if (count == 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    final ArrayList<String> strings = new ArrayList<String>(count);
    for (int i = 0; i < count; i++) {
      strings.add(readLimitedString());
    }
    return strings.toArray(new String[count]);
  }
}
