package com.intellij.compiler.make;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 1, 2008
 */
public class StorageMethodId extends StorageClassId{
  private int myMethodName;
  private int myMethodDescriptor;

  public StorageMethodId(int QName, int methodName, int methodDescriptor) {
    super(QName);
    myMethodName = methodName;
    myMethodDescriptor = methodDescriptor;
  }

  public int getMethodName() {
    return myMethodName;
  }

  public void setMethodName(int methodName) {
    myMethodName = methodName;
  }

  public int getMethodDescriptor() {
    return myMethodDescriptor;
  }

  public void setMethodDescriptor(int methodDescriptor) {
    myMethodDescriptor = methodDescriptor;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StorageMethodId that = (StorageMethodId)o;
    return myMethodDescriptor == that.myMethodDescriptor && myMethodName == that.myMethodName && getClassQName() == that.getClassQName();
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myMethodName;
    result = 31 * result + myMethodDescriptor;
    return result;
  }
}