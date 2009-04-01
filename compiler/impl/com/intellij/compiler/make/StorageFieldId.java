package com.intellij.compiler.make;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 1, 2008
 */
public final class StorageFieldId extends StorageClassId{
  private final int myFieldName;

  public StorageFieldId(int QName, int fieldName) {
    super(QName);
    myFieldName = fieldName;
  }

  public int getFieldName() {
    return myFieldName;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof StorageFieldId)) return false;

    final StorageFieldId that = (StorageFieldId)o;
    return myFieldName == that.myFieldName && getClassQName() == that.getClassQName();
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myFieldName;
    return result;
  }
}
