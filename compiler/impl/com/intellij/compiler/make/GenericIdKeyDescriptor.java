package com.intellij.compiler.make;

import com.intellij.util.io.KeyDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 1, 2008
*/
class GenericIdKeyDescriptor implements KeyDescriptor<StorageClassId> {
  public static final GenericIdKeyDescriptor INSTANCE = new GenericIdKeyDescriptor();
  
  private static final int KIND_FIELD = 1;
  private static final int KIND_METHOD = 2;
  private static final int KIND_CLASS = 3;

  public int getHashCode(StorageClassId value) {
    return value.hashCode();
  }

  public boolean isEqual(StorageClassId val1, StorageClassId val2) {
    return val1.equals(val2);
  }

  public void save(DataOutput out, StorageClassId value) throws IOException {
    if (value instanceof StorageFieldId) {
      out.writeByte(KIND_FIELD);
      final StorageFieldId _value = (StorageFieldId)value;
      out.writeInt(_value.getClassQName());
      out.writeInt(_value.getFieldName());
    }
    else if (value instanceof StorageMethodId) {
      out.writeByte(KIND_METHOD);
      final StorageMethodId _value = (StorageMethodId)value;
      out.writeInt(_value.getClassQName());
      out.writeInt(_value.getMethodName());
      out.writeInt(_value.getMethodDescriptor());
    }
    else {
      out.writeByte(KIND_CLASS);
      out.writeInt(value.getClassQName());
    }

  }

  public StorageClassId read(DataInput in) throws IOException {
    final byte kind = in.readByte();
    switch (kind) {
      case KIND_METHOD:{
        int qName = in.readInt();
        int methodName = in.readInt();
        int methodDescriptor = in.readInt();
        return new StorageMethodId(qName, methodName, methodDescriptor);
      }
      case KIND_FIELD: {
        int qName = in.readInt();
        int fieldName = in.readInt();
        return new StorageFieldId(qName, fieldName);
      }
      default : return new StorageClassId(in.readInt());
    }
  }
}
