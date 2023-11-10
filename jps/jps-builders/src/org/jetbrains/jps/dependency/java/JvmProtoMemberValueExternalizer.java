// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.Externalizer;
import org.jetbrains.org.objectweb.asm.Type;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Array;

enum JvmProtoMemberValueExternalizer implements Externalizer<Object> {
  NONE(null) {
    @Override
    public void save(DataOutput out, Object v) {
    }

    @Override
    public Object load(DataInput in) {
      return null;
    }
  },
  STRING(String.class) {
    @Override
    public void save(DataOutput out, Object v) throws IOException {
      out.writeUTF((String)v);
    }

    @Override
    public Object load(DataInput in) throws IOException {
      return in.readUTF();
    }
  },
  INTEGER(Integer.class) {
    @Override
    public void save(DataOutput out, Object v) throws IOException {
      out.writeInt((Integer)v);
    }

    @Override
    public Object load(DataInput in) throws IOException {
      return in.readInt();
    }
  },
  LONG(Long.class) {
    @Override
    public void save(DataOutput out, Object v) throws IOException {
      out.writeLong((Long)v);
    }

    @Override
    public Object load(DataInput in) throws IOException {
      return in.readLong();
    }
  },
  FLOAT(Float.class) {
    @Override
    public void save(DataOutput out, Object v) throws IOException {
      out.writeFloat((Float)v);
    }

    @Override
    public Object load(DataInput in) throws IOException {
      return in.readFloat();
    }
  },
  DOUBLE(Double.class) {
    @Override
    public void save(DataOutput out, Object v) throws IOException {
      out.writeDouble((Double)v);
    }

    @Override
    public Object load(DataInput in) throws IOException {
      return in.readDouble();
    }
  },
  TYPE(Type.class) {
    @Override
    public void save(DataOutput out, Object v) throws IOException {
      out.writeUTF(((Type)v).getDescriptor());
    }

    @Override
    public Object load(DataInput in) throws IOException {
      return Type.getType(in.readUTF());
    }
  },
  ARRAY(Array.class) {

    @Override
    public void save(DataOutput out, Object val) throws IOException {
      final int length = Array.getLength(val);
      JvmProtoMemberValueExternalizer ext = find(length > 0? Array.get(val, 0).getClass() : val.getClass().getComponentType());
      out.writeInt(ext.ordinal());
      if (ext != NONE) {
        out.writeInt(length);
        for (int idx = 0; idx < length; idx++) {
          ext.save(out, Array.get(val, idx));
        }
      }
    }

    @Override
    public Object load(DataInput in) throws IOException {
      int ord = in.readInt();
      if (NONE.ordinal() != ord) {
        for (JvmProtoMemberValueExternalizer ext : values()) {
          if (ext.ordinal() == ord) {
            int length = in.readInt();
            final Object array = Array.newInstance(ext.dataType, length);
            for (int idx = 0; idx < length; idx++) {
              Array.set(array, idx, ext.load(in));
            }
            return array;
          }
        }
      }
      return NONE.load(in);
    }
  }
  ;

  @Nullable
  private final Class<?> dataType;

  JvmProtoMemberValueExternalizer(@Nullable Class<?> dataType) {
    this.dataType = dataType;
  }

  @Override
  public abstract void save(DataOutput out, Object v) throws IOException;

  @Override
  public abstract Object load(DataInput in) throws IOException;

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

  static void write(DataOutput out, @Nullable Object value) throws IOException {
    JvmProtoMemberValueExternalizer ext = find(value != null? value.getClass() : null);
    out.writeInt(ext.ordinal());
    ext.save(out, value);
  }

  static Object read(DataInput in) throws IOException {
    int ord = in.readInt();
    for (JvmProtoMemberValueExternalizer ext : values()) {
      if (ext.ordinal() == ord) {
        return ext.load(in);
      }
    }
    return NONE.load(in);
  }
}
