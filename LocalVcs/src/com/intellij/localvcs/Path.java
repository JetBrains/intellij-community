package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Path {
  private final String myName;

  public Path(String name) {
    myName = name;
  }

  public boolean hasParent() {
    return getParent() != null;
  }

  public Path getParent() {
    List<String> parts = getParts();
    parts.remove(parts.size() - 1);

    if (parts.isEmpty()) return null;

    String result = "";

    for (String p : parts) {
      result += p + "/";
    }

    return new Path(result.substring(0, result.length() - 1));
  }

  public Path getTail() {
    return new Path(getParts().get(getParts().size() - 1));
  }

  public List<String> getParts() {
    List<String> result = new ArrayList<String>();

    StringTokenizer t = new StringTokenizer(myName, "/");
    while (t.hasMoreTokens()) {
      result.add(t.nextToken());
    }

    return result;
  }

  public Path appendedWith(Path tail) {
    return new Path(myName + "/" + tail.myName);
  }

  public Path renamedWith(Path newName) {
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
    return myName.equals(((Path)o).myName);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
