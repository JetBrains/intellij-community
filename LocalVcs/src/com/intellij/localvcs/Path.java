package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Path {
  // todo support c:/ notation
  private static final String DELIM = "/";
  private String myPath;

  public Path(String path) {
    myPath = path;
  }

  public Path(Stream s) throws IOException {
    myPath = s.readString();
  }

  public void write(Stream s) throws IOException {
    s.writeString(myPath);
  }

  public String getName() {
    return getParts().get(getParts().size() - 1);
  }

  public String getPath() {
    return myPath;
  }

  public Path getParent() {
    List<String> parts = getParts();
    parts.remove(parts.size() - 1);

    if (parts.isEmpty()) return null; // todo throw exception?

    String result = "";
    for (String p : parts) {
      result += p + DELIM;
    }

    return new Path(result.substring(0, result.length() - 1));
  }

  public Path appendedWith(String tail) {
    return new Path(myPath + DELIM + tail);
  }

  public Path renamedWith(String newName) {
    if (getParent() == null) return new Path(newName);
    return getParent().appendedWith(newName);
  }

  protected List<String> getParts() {
    List<String> result = new ArrayList<String>();

    if (myPath.startsWith("/")) {
      result.add("");
    }

    StringTokenizer t = new StringTokenizer(myPath, DELIM);
    while (t.hasMoreTokens()) {
      result.add(t.nextToken());
    }

    return result;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + myPath + ")";
  }

  @Override
  public boolean equals(Object o) {
    // todo possible we should remove these methods after optimization of
    // todo Entry child searching
    if (o == null || !o.getClass().equals(getClass())) return false;
    return myPath.equals(((Path)o).myPath);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
