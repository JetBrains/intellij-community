/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.org.objectweb.asm.Type;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.util.Set;

/**
 * @author: db
 */
abstract class ProtoMember extends Proto {

  @NotNull
  public final TypeRepr.AbstractType myType;
  public final Object myValue;

  private static abstract class DataDescriptor<T> {
    public static final DataDescriptor NONE = new DataDescriptor(0, null) {
      @Override
      public Object load(DataInput out) {
        return null;
      }
      @Override
      public void save(DataOutput out, Object value) throws IOException {
      }
    };
    public static final DataDescriptor<String> STRING = new DataDescriptor<String>(1, String.class) {
      @Override
      public String load(DataInput in) throws IOException {
        return RW.readUTF(in);
      }
      @Override
      public void save(DataOutput out, String value) throws IOException {
        RW.writeUTF(out, value);
      }
    };
    public static final DataDescriptor<Integer> INTEGER = new DataDescriptor<Integer>(2, Integer.class) {
      @Override
      public Integer load(DataInput in) throws IOException {
        return DataInputOutputUtil.readINT(in);
      }

      @Override
      public void save(DataOutput out, Integer value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.intValue());
      }
    };
    public static final DataDescriptor<Long> LONG = new DataDescriptor<Long>(3, Long.class) {
      @Override
      public Long load(DataInput in) throws IOException {
        return in.readLong();
      }

      @Override
      public void save(DataOutput out, Long value) throws IOException {
        out.writeLong(value.longValue());
      }
    };
    public static final DataDescriptor<Float> FLOAT = new DataDescriptor<Float>(4, Float.class) {
      @Override
      public Float load(DataInput in) throws IOException {
        return in.readFloat();
      }

      @Override
      public void save(DataOutput out, Float value) throws IOException {
        out.writeFloat(value.floatValue());
      }
    };
    public static final DataDescriptor<Double> DOUBLE = new DataDescriptor<Double>(5, Double.class) {
      @Override
      public Double load(DataInput in) throws IOException {
        return in.readDouble();
      }

      @Override
      public void save(DataOutput out, Double value) throws IOException {
        out.writeDouble(value.doubleValue());
      }
    };
    public static final DataDescriptor<Type> TYPE = new DataDescriptor<Type>(6, Type.class) {
      @Override
      public Type load(DataInput in) throws IOException {
        return Type.getType(RW.readUTF(in));
      }

      @Override
      public void save(DataOutput out, Type value) throws IOException {
        RW.writeUTF(out, value.getDescriptor());
      }
    };

    private final byte myId;
    @Nullable
    private final Class<T> myDataType;

    private DataDescriptor(int id, Class<T> dataType) {
      myId = (byte)id;
      myDataType = dataType;
    }

    public byte getId() {
      return myId;
    }

    @Nullable
    public Class<T> getDataType() {
      return myDataType;
    }

    public abstract void save(DataOutput out, T value) throws IOException;
    public abstract T load(DataInput in) throws IOException;

    @NotNull
    public static DataDescriptor findById(byte tag) {
      if (STRING.getId() == tag) {
        return STRING;
      }
      if (INTEGER.getId() == tag) {
        return INTEGER;
      }
      if (LONG.getId() == tag) {
        return LONG;
      }
      if (FLOAT.getId() == tag) {
        return FLOAT;
      }
      if (DOUBLE.getId() == tag) {
        return DOUBLE;
      }
      if (TYPE.getId() == tag) {
        return TYPE;
      }
      if (NONE.getId() == tag) {
        return NONE;
      }
      assert false : "Unknown descriptor tag: " + tag;
      return NONE;
    }

    public static DataDescriptor findByValueType(@Nullable Class<?> dataType) {
      if (dataType != null) {
        if (dataType.equals(STRING.getDataType())) {
          return STRING;
        }
        if (dataType.equals(INTEGER.getDataType())) {
          return INTEGER;
        }
        if (dataType.equals(LONG.getDataType())) {
          return LONG;
        }
        if (dataType.equals(FLOAT.getDataType())) {
          return FLOAT;
        }
        if (dataType.equals(DOUBLE.getDataType())) {
          return DOUBLE;
        }
        //noinspection ConstantConditions
        if (TYPE.getDataType().isAssignableFrom(dataType)) {
          return TYPE;
        }
      }
      return NONE;
    }
  }

  public boolean hasValue() {
    return myValue != null;
  }

  protected ProtoMember(final int access,
                        final int signature,
                        final int name,
                        @NotNull
                        final TypeRepr.AbstractType t,
                        @NotNull
                        Set<TypeRepr.ClassType> annotations,
                        final Object value) {
    super(access, signature, name, annotations);
    myType = t;
    myValue = value;
  }

  protected ProtoMember(final DependencyContext context, final DataInput in) {
    super(context, in);
    try {
      myType = TypeRepr.externalizer(context).read(in);
      myValue = loadTyped(in);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  private static Object loadTyped(final DataInput in) {
    try {
      final byte tag = in.readByte();
      if (tag < 0) {
        // is array
        final int length = DataInputOutputUtil.readINT(in);
        final DataDescriptor descriptor = DataDescriptor.findById((byte)-tag);
        final Object array = Array.newInstance(descriptor.getDataType(), length);
        for (int idx = 0; idx < length; idx++) {
          Array.set(array, idx, descriptor.load(in));
        }
        return array;
      }
      return DataDescriptor.findById(tag).load(in);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  public void save(final DataOutput out) {
    super.save(out);
    myType.save(out);

    try {
      final Object val = myValue;
      final Class valueType = val != null? val.getClass() : null;
      if (valueType != null && valueType.isArray()) {
        final int length = Array.getLength(val);
        final Class dataType = length > 0? Array.get(val, 0).getClass() : valueType.getComponentType();
        final DataDescriptor descriptor = DataDescriptor.findByValueType(dataType);
        out.writeByte(-descriptor.getId());
        if (descriptor != DataDescriptor.NONE) {
          DataInputOutputUtil.writeINT(out, length);
          for (int idx = 0; idx < length; idx++) {
            final Object element = Array.get(val, idx);
            //noinspection unchecked
            descriptor.save(out, element);
          }
        }
      }
      else {
        final DataDescriptor descriptor = DataDescriptor.findByValueType(valueType);
        out.writeByte(descriptor.getId());
        //noinspection unchecked
        descriptor.save(out, val);
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public Difference difference(final Proto past) {
    final ProtoMember m = (ProtoMember)past;
    final Difference diff = super.difference(past);
    int base = diff.base();

    if (!m.myType.equals(myType)) {
      base |= Difference.TYPE;
    }

    switch ((myValue == null ? 0 : 1) + (m.myValue == null ? 0 : 2)) {
      case 3:
        if (!myValue.equals(m.myValue)) {
          base |= Difference.VALUE;
        }
        break;

      case 2:
        base |= Difference.VALUE;
        break;

      case 1:
        base |= Difference.VALUE;
        break;

      case 0:
        break;
    }

    final int newBase = base;

    return new DifferenceImpl(diff) {
      @Override
      public int base() {
        return newBase;
      }

      @Override
      public boolean no() {
        return newBase == NONE && super.no();
      }

      @Override
      public boolean hadValue() {
        return ((ProtoMember)past).hasValue();
      }
    };
  }

  public void toStream(final DependencyContext context, final PrintStream stream) {
    super.toStream(context, stream);
    stream.print("          Type       : ");
    stream.println(myType.getDescr(context));

    stream.print("          Value      : ");
    stream.println(myValue == null ? "<null>" : myValue.toString());
  }
}
