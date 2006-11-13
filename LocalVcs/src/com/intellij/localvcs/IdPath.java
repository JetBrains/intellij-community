package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IdPath {
  private List<Integer> myPath;

  public IdPath(Integer... parts) {
    myPath = Arrays.asList(parts);
  }

  public IdPath appendedWith(Integer id) {
    List<Integer> newPath = new ArrayList<Integer>(myPath);
    newPath.add(id);
    return new IdPath(newPath.toArray(new Integer[0]));
  }

  public boolean isPrefixOf(IdPath p) {
    // todo does it stll needed
    if (myPath.size() > p.myPath.size()) return false;

    for (int i = 0; i < myPath.size(); i++) {
      if (!myPath.get(i).equals(p.myPath.get(i))) return false;
    }

    return true;
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
