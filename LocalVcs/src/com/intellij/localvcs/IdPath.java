package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IdPath {
  private List<Integer> myParts;

  public IdPath(Integer... parts) {
    this(Arrays.asList(parts));
  }

  private IdPath(List<Integer> parts) {
    myParts = parts;
  }

  public IdPath(Stream s) throws IOException {
    myParts = new ArrayList<Integer>();

    int count = s.readInteger();
    while (count-- > 0) {
      myParts.add(s.readInteger());
    }
  }

  public void write(Stream s) throws IOException {
    s.writeInteger(myParts.size());
    for (Integer id : myParts) {
      s.writeInteger(id);
    }
  }

  public Integer getName() {
    return myParts.get(myParts.size() - 1);
  }

  public IdPath appendedWith(Integer id) {
    List<Integer> newPath = new ArrayList<Integer>(myParts);
    newPath.add(id);
    return new IdPath(newPath);
  }

  public boolean contains(Integer id) {
    return myParts.contains(id);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + myParts + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !o.getClass().equals(getClass())) return false;
    return myParts.equals(((IdPath)o).myParts);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
