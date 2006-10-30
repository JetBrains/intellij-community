package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Filename {
  private String myName;

  public Filename(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public Filename getParent() {
    List<String> parts = getParts();
    parts.remove(parts.size() - 1);

    if (parts.isEmpty()) return null;

    String result = "";

    for (String p : parts) {
      result += p + "/";
    }

    return new Filename(result.substring(0, result.length() - 1));
  }

  public List<String> getParts() {
    List<String> result = new ArrayList<String>();

    StringTokenizer t = new StringTokenizer(myName, "/");
    while (t.hasMoreTokens()) {
      result.add(t.nextToken());
    }

    return result;
  }

  public Filename with(Filename tail) {
    return new Filename(myName + "/" + tail.myName);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + myName + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || o.getClass() != getClass()) return false;
    return myName.equals(((Filename)o).myName);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
