// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.Externalizer;
import org.jetbrains.jps.dependency.impl.RW;
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
      RW.writeUTF(out, (String)v);
    }

    @Override
    public Object load(DataInput in) throws IOException {
      return RW.readUTF(in);
    }
  },
  INTEGER(Integer.class) {
    @Override
    public void save(DataOutput out, Object v) throws IOException {
      RW.writeINT(out, (Integer)v);
    }

    @Override
    public Object load(DataInput in) throws IOException {
      return RW.readINT(in);
    }
  },
  LONG(Long.class) {
    @Override
    public void save(DataOutput out, Object v) throws IOException {
      RW.writeLONG(out, (Long)v);
    }

    @Override
    public Object load(DataInput in) throws IOException {
      return RW.readLONG(in);
    }
  },
  FLOAT(Float.class) {
    @Override
    public void save(DataOutput out, Object v) throws IOException {
      RW.writeFLOAT(out, (Float)v);
    }

    @Override
    public Object load(DataInput in) throws IOException {
      return RW.readFLOAT(in);
    }
  },
  DOUBLE(Double.class) {
    @Override
    public void save(DataOutput out, Object v) throws IOException {
      RW.writeDOUBLE(out, (Double)v);
    }

    @Override
    public Object load(DataInput in) throws IOException {
      return RW.readDOUBLE(in);
    }
  },
  TYPE(Type.class) {
    @Override
    public void save(DataOutput out, Object v) throws IOException {
      RW.writeUTF(out, ((Type)v).getDescriptor());
    }

    @Override
    public Object load(DataInput in) throws IOException {
      return Type.getType(RW.readUTF(in));
    }
  },
  ARRAY(Array.class) {

    @Override
    public void save(DataOutput out, Object val) throws IOException {
      final int length = Array.getLength(val);
      JvmProtoMemberValueExternalizer ext = find(length > 0? Array.get(val, 0).getClass() : val.getClass().getComponentType());
      RW.writeINT(out, ext.ordinal());
      if (ext != NONE) {
        RW.writeINT(out, length);
        for (int idx = 0; idx < length; idx++) {
          ext.save(out, Array.get(val, idx));
        }
      }
    }

    @Override
    public Object load(DataInput in) throws IOException {
      int ord = RW.readINT(in);
      if (NONE.ordinal() != ord) {
        for (JvmProtoMemberValueExternalizer ext : values()) {
          if (ext.ordinal() == ord) {
            int length = RW.readINT(in);
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
    RW.writeINT(out, ext.ordinal());
    ext.save(out, value);
  }

  static Object read(DataInput in) throws IOException {
    int ord = RW.readINT(in);
    for (JvmProtoMemberValueExternalizer ext : values()) {
      if (ext.ordinal() == ord) {
        return ext.load(in);
      }
    }
    return NONE.load(in);
  }
}
