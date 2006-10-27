package com.intellij.localvcs;

public class Filename {
  private String myName;

  public Filename(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
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
