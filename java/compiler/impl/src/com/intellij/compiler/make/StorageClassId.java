package com.intellij.compiler.make;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 1, 2008
 */
public class StorageClassId {
  private final int myQName;

  public StorageClassId(int QName) {
    myQName = QName;
  }

  public int getClassQName() {
    return myQName;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof StorageClassId)) return false;

    if (myQName != ((StorageClassId)o).myQName) return false;

    return true;
  }

  public int hashCode() {
    return myQName;
  }

}
