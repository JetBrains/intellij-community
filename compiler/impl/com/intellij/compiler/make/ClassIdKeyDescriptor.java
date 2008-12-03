package com.intellij.compiler.make;

import com.intellij.util.io.KeyDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 1, 2008
 */
class ClassIdKeyDescriptor implements KeyDescriptor<StorageClassId> {
  public static final ClassIdKeyDescriptor INSTANCE = new ClassIdKeyDescriptor();

  public int getHashCode(StorageClassId value) {
    return value.hashCode();
  }

  public boolean isEqual(StorageClassId val1, StorageClassId val2) {
    return val1.equals(val2);
  }

  public void save(DataOutput out, StorageClassId value) throws IOException {
    out.writeInt(value.getClassQName());
  }

  public StorageClassId read(DataInput in) throws IOException {
    return new StorageClassId(in.readInt());
  }
}