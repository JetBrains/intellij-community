package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class FileName {
  private final String myName;

  public FileName(String name) {
    myName = name;
  }

  public String getPath() {
    return myName;
  }

  public boolean hasParent() {
    return getParent() != null;
  }

  public FileName getParent() {
    List<String> parts = getParts();
    parts.remove(parts.size() - 1);

    if (parts.isEmpty()) return null;

    String result = "";

    for (String p : parts) {
      result += p + "/";
    }

    return new FileName(result.substring(0, result.length() - 1));
  }

  public FileName getTail() {
    return new FileName(getParts().get(getParts().size() - 1));
  }

  public List<String> getParts() {
    List<String> result = new ArrayList<String>();

    StringTokenizer t = new StringTokenizer(myName, "/");
    while (t.hasMoreTokens()) {
      result.add(t.nextToken());
    }

    return result;
  }

  public FileName appendedWith(FileName tail) {
    return new FileName(myName + "/" + tail.myName);
  }

  public FileName renamedWith(FileName newName) {
    if (!hasParent()) return newName;
    return getParent().appendedWith(newName);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + myName + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || o.getClass() != getClass()) return false;
    return myName.equals(((FileName)o).myName);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
