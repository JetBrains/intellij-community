package com.intellij.compiler.make;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 1, 2008
 */
public class StorageFieldId extends StorageClassId{
  private int myFieldName;

  public StorageFieldId(int QName, int fieldName) {
    super(QName);
    myFieldName = fieldName;
  }

  public int getFieldName() {
    return myFieldName;
  }

  public void setFieldName(int fieldName) {
    myFieldName = fieldName;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StorageFieldId that = (StorageFieldId)o;
    return myFieldName == that.myFieldName && getClassQName() == that.getClassQName();
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myFieldName;
    return result;
  }
}
