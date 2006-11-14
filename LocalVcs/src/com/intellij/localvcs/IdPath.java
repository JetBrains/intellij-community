package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IdPath {
  private List<Integer> myPath;

  public IdPath(Integer... parts) {
    myPath = Arrays.asList(parts);
  }

  public IdPath(Stream s) throws IOException {
    myPath = new ArrayList<Integer>();

    int count = s.readInteger();
    while (count-- > 0) {
      myPath.add(s.readInteger());
    }
  }

  public void write(Stream s) throws IOException {
    s.writeInteger(myPath.size());
    for (Integer id : myPath) {
      s.writeInteger(id);
    }
  }

  public Integer getName() {
    return myPath.get(myPath.size() - 1);
  }

  public IdPath appendedWith(Integer id) {
    List<Integer> newPath = new ArrayList<Integer>(myPath);
    newPath.add(id);
    return new IdPath(newPath.toArray(new Integer[0]));
  }

  public boolean contains(Integer id) {
    return myPath.contains(id);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + myPath + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !o.getClass().equals(getClass())) return false;
    return myPath.equals(((IdPath)o).myPath);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
