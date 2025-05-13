// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.Externalizer;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.org.objectweb.asm.Type;

import java.io.IOException;
import java.lang.reflect.Array;

enum JvmProtoMemberValueExternalizer implements Externalizer<Object> {
  NONE(null) {
    @Override
    public void save(GraphDataOutput out, Object v) {
    }

    @Override
    public Object load(GraphDataInput in) {
      return null;
    }
  },
  STRING(String.class) {
    @Override
    public void save(GraphDataOutput out, Object v) throws IOException {
      out.writeUTF((String)v);
    }

    @Override
    public Object load(GraphDataInput in) throws IOException {
      return in.readUTF();
    }
  },
  INTEGER(Integer.class) {
    @Override
    public void save(GraphDataOutput out, Object v) throws IOException {
      out.writeInt((Integer)v);
    }

    @Override
    public Object load(GraphDataInput in) throws IOException {
      return in.readInt();
    }
  },
  LONG(Long.class) {
    @Override
    public void save(GraphDataOutput out, Object v) throws IOException {
      out.writeLong((Long)v);
    }

    @Override
    public Object load(GraphDataInput in) throws IOException {
      return in.readLong();
    }
  },
  FLOAT(Float.class) {
    @Override
    public void save(GraphDataOutput out, Object v) throws IOException {
      out.writeFloat((Float)v);
    }

    @Override
    public Object load(GraphDataInput in) throws IOException {
      return in.readFloat();
    }
  },
  DOUBLE(Double.class) {
    @Override
    public void save(GraphDataOutput out, Object v) throws IOException {
      out.writeDouble((Double)v);
    }

    @Override
    public Object load(GraphDataInput in) throws IOException {
      return in.readDouble();
    }
  },
  TYPE(Type.class) {
    @Override
    public void save(GraphDataOutput out, Object v) throws IOException {
      out.writeUTF(((Type)v).getDescriptor());
    }

    @Override
    public Object load(GraphDataInput in) throws IOException {
      return Type.getType(in.readUTF());
    }
  },
  ARRAY(Array.class) {

    @Override
    public void save(GraphDataOutput out, Object val) throws IOException {
      final int length = Array.getLength(val);
      JvmProtoMemberValueExternalizer ext = find(getDataType(length > 0? Array.get(val, 0).getClass() : val.getClass().getComponentType()));
      out.writeInt(ext.ordinal());
      if (ext != NONE) {
        out.writeInt(length);
        for (int idx = 0; idx < length; idx++) {
          ext.save(out, Array.get(val, idx));
        }
      }
    }

    @Override
    public Object load(GraphDataInput in) throws IOException {
      int ord = in.readInt();
      if (NONE.ordinal() != ord) {
        for (JvmProtoMemberValueExternalizer ext : values()) {
          if (ext.ordinal() == ord) {
            int length = in.readInt();
            final Object array = Array.newInstance(getArrayElementType(ext.dataType), length);
            for (int idx = 0; idx < length; idx++) {
              Array.set(array, idx, ext.load(in));
            }
            return array;
          }
        }
      }
      return NONE.load(in);
    }

    private Class<?> getArrayElementType(Class<?> dataType) {
      if (Character.class.equals(dataType)) {
        return char.class;
      }
      if (Byte.class.equals(dataType)) {
        return byte.class;
      }
      if (Short.class.equals(dataType)) {
        return short.class;
      }
      if (Integer.class.equals(dataType)) {
        return int.class;
      }
      if (Long.class.equals(dataType)) {
        return long.class;
      }
      if (Float.class.equals(dataType)) {
        return float.class;
      }
      if (Double.class.equals(dataType)) {
        return double.class;
      }
      if (Boolean.class.equals(dataType)) {
        return boolean.class;
      }
      return dataType;
    }

    private Class<?> getDataType(Class<?> arrayElementType) {
      if (char.class.equals(arrayElementType)) {
        return Character.class;
      }
      if (byte.class.equals(arrayElementType)) {
        return Byte.class;
      }
      if (short.class.equals(arrayElementType)) {
        return Short.class;
      }
      if (int.class.equals(arrayElementType)) {
        return Integer.class;
      }
      if (long.class.equals(arrayElementType)) {
        return Long.class;
      }
      if (float.class.equals(arrayElementType)) {
        return Float.class;
      }
      if (double.class.equals(arrayElementType)) {
        return Double.class;
      }
      if (boolean.class.equals(arrayElementType)) {
        return Boolean.class;
      }
      return arrayElementType;
    }
  },
  BOOLEAN(Boolean.class) {
    @Override
    public void save(GraphDataOutput out, Object v) throws IOException {
      out.writeBoolean((Boolean)v);
    }

    @Override
    public Object load(GraphDataInput in) throws IOException {
      return in.readBoolean();
    }
  },
  CHARACTER(Character.class) {
    @Override
    public void save(GraphDataOutput out, Object v) throws IOException {
      out.writeChar((Character)v);
    }

    @Override
    public Object load(GraphDataInput in) throws IOException {
      return in.readChar();
    }
  },
  BYTE(Byte.class) {
    @Override
    public void save(GraphDataOutput out, Object v) throws IOException {
      out.writeByte((Byte)v);
    }

    @Override
    public Object load(GraphDataInput in) throws IOException {
      return in.readByte();
    }
  },
  SHORT(Short.class) {
    @Override
    public void save(GraphDataOutput out, Object v) throws IOException {
      out.writeShort((Short)v);
    }

    @Override
    public Object load(GraphDataInput in) throws IOException {
      return in.readShort();
    }
  }
  ;

  private final @Nullable Class<?> dataType;

  JvmProtoMemberValueExternalizer(@Nullable Class<?> dataType) {
    this.dataType = dataType;
  }

  @Override
  public Object[] createStorage(int size) {
    return new Object[size];
  }
  
  @Override
  public abstract void save(GraphDataOutput out, Object v) throws IOException;

  @Override
  public abstract Object load(GraphDataInput in) throws IOException;

  private static JvmProtoMemberValueExternalizer find(@Nullable Class<?> dataType) {
    if (dataType != null) {
      if (dataType.isArray()) {
        return ARRAY;
      }
      for (JvmProtoMemberValueExternalizer ext : values()) {
        if (ext.dataType != null && ext.dataType.isAssignableFrom(dataType)) {
          return ext;
        }
      }
    }
    return NONE;
  }

  static void write(GraphDataOutput out, @Nullable Object value) throws IOException {
    JvmProtoMemberValueExternalizer ext = find(value != null? value.getClass() : null);
    out.writeInt(ext.ordinal());
    ext.save(out, value);
  }

  static Object read(GraphDataInput in) throws IOException {
    int ord = in.readInt();
    for (JvmProtoMemberValueExternalizer ext : values()) {
      if (ext.ordinal() == ord) {
        return ext.load(in);
      }
    }
    return null;
  }
}
