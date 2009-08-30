package com.intellij.compiler.make;

import com.intellij.util.io.KeyDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 1, 2008
*/
class MethodIdKeyDescriptor implements KeyDescriptor<StorageMethodId> {
  public static final MethodIdKeyDescriptor INSTANCE = new MethodIdKeyDescriptor();

  public int getHashCode(StorageMethodId value) {
    return value.hashCode();
  }

  public boolean isEqual(StorageMethodId val1, StorageMethodId val2) {
    return val1.equals(val2);
  }

  public void save(DataOutput out, StorageMethodId value) throws IOException {
    out.writeInt(value.getClassQName());
    out.writeInt(value.getMethodName());
    out.writeInt(value.getMethodDescriptor());
  }

  public StorageMethodId read(DataInput in) throws IOException {
    final int qName = in.readInt();
    final int methodName = in.readInt();
    final int methodDescriptor = in.readInt();
    return new StorageMethodId(qName, methodName, methodDescriptor);
  }
}