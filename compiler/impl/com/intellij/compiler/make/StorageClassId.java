package com.intellij.compiler.make;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 1, 2008
 */
public class StorageClassId {
  private int myQName;

  public StorageClassId(int QName) {
    myQName = QName;
  }

  public int getClassQName() {
    return myQName;
  }

  public void setQName(int QName) {
    myQName = QName;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StorageClassId that = (StorageClassId)o;

    if (myQName != that.myQName) return false;

    return true;
  }

  public int hashCode() {
    return myQName;
  }

}
