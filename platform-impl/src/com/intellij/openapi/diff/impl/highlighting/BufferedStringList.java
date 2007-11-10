package com.intellij.openapi.diff.impl.highlighting;

import java.util.ArrayList;

class BufferedStringList {
  private final ArrayList<String> myStrings = new ArrayList<String>();
  private final StringBuffer myLast = new StringBuffer();

  public void add(String string) {
    flushLast();
    myStrings.add(string);
  }

  public void appendToLast(String string) {
    myLast.append(string);
  }

  public void flushLast() {
    if (myLast.length() > 0) {
      myStrings.add(myLast.toString());
      myLast.setLength(0);
    }
  }

  public String[] toArray() {
    flushLast();
    return myStrings.toArray(new String[myStrings.size()]);
  }
}
