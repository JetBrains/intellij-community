package com.intellij.localvcs;

import com.intellij.util.ArrayUtil;

import java.io.IOException;
import java.util.Arrays;

// todo do something with this class - i dont like it

// todo it also has very poor performance
public class IdPath {
  private int[] myParts;

  public IdPath(int... parts) {
    myParts = parts;
  }

  public IdPath(Stream s) throws IOException {
    myParts = new int[s.readInteger()];
    for (int i = 0; i < myParts.length; i++) {
      myParts[i] = s.readInteger();
    }
  }

  public void write(Stream s) throws IOException {
    s.writeInteger(myParts.length);
    for (Integer id : myParts) {
      s.writeInteger(id);
    }
  }

  public Integer getName() {
    return myParts[myParts.length - 1];
  }

  public IdPath getParent() {
    if (myParts.length == 1) return null;
    int[] newPath = new int[myParts.length - 1];
    System.arraycopy(myParts, 0, newPath, 0, newPath.length);
    return new IdPath(newPath);  
  }

  public IdPath appendedWith(Integer id) {
    // todo use Arrays.copyOf after going to 1.6 
    int[] newPath = new int[myParts.length + 1];
    System.arraycopy(myParts, 0, newPath, 0, myParts.length);
    newPath[newPath.length - 1] = id;
    return new IdPath(newPath);
  }

  public boolean contains(Integer id) {
    for (Integer part : myParts) {
      if (part.equals(id)) return true;
    }
    return false;
  }

  public boolean rootEquals(int id) {
    return myParts[0] == id;
  }

  public IdPath withoutRoot() {
    int[] newPath = new int[myParts.length - 1];
    System.arraycopy(myParts, 1, newPath, 0, newPath.length);
    return new IdPath(newPath);
  }

  @Override
  public String toString() {
    String result = "";
    for (int part : myParts) {
      result += part + ".";
    }
    return result.substring(0, result.length() - 1);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !o.getClass().equals(getClass())) return false;
    return Arrays.equals(myParts, ((IdPath)o).myParts);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
