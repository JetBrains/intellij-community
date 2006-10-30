package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Path {
  private final String myPath;

  public Path(String path) {
    myPath = path;
  }

  public String getValue() {
    // todo remove this method
    return myPath;
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

  public String getTail() {
    return getParts().get(getParts().size() - 1);
  }

  public List<String> getParts() {
    List<String> result = new ArrayList<String>();

    StringTokenizer t = new StringTokenizer(myPath, "/");
    while (t.hasMoreTokens()) {
      result.add(t.nextToken());
    }

    return result;
  }

  public Path appendedWith(String tail) {
    return new Path(myPath + "/" + tail);
  }

  public Path renamedWith(String newName) {
    if (!hasParent()) return new Path(newName);
    return getParent().appendedWith(newName);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + myPath + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || o.getClass() != getClass()) return false;
    return myPath.equals(((Path)o).myPath);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
