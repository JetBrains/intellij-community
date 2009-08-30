package com.intellij.compiler.make;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 1, 2008
 */
public final class StorageMethodId extends StorageClassId{
  private final int myMethodName;
  private final int myMethodDescriptor;

  public StorageMethodId(int QName, int methodName, int methodDescriptor) {
    super(QName);
    myMethodName = methodName;
    myMethodDescriptor = methodDescriptor;
  }

  public int getMethodName() {
    return myMethodName;
  }

  public int getMethodDescriptor() {
    return myMethodDescriptor;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof StorageMethodId)) return false;

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