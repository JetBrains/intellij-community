package com.intellij.database.datagrid;

/**
 * @author gregsh
 */
public abstract class Index {
  public final int value;

  public Index(int value) {
    this.value = value;
  }

  public int asInteger() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (getClass() != o.getClass()) {
      assert o instanceof Index : "Comparing Model indices with View indices!";
      return false;
    }

    Index index = (Index)o;
    return value == index.value;
  }

  @Override
  public int hashCode() {
    return value;
  }
}
