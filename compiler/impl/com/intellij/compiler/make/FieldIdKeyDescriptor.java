package com.intellij.compiler.make;

import com.intellij.util.io.KeyDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 1, 2008
*/
class FieldIdKeyDescriptor implements KeyDescriptor<StorageFieldId> {
  public static final FieldIdKeyDescriptor INSTANCE = new FieldIdKeyDescriptor();

  public int getHashCode(StorageFieldId value) {
    return value.hashCode();
  }

  public boolean isEqual(StorageFieldId val1, StorageFieldId val2) {
    return val1.equals(val2);
  }

  public void save(DataOutput out, StorageFieldId value) throws IOException {
    out.writeInt(value.getClassQName());
    out.writeInt(value.getFieldName());
  }

  public StorageFieldId read(DataInput in) throws IOException {
    final int qName = in.readInt();
    final int fieldName = in.readInt();
    return new StorageFieldId(qName, fieldName);
  }
}