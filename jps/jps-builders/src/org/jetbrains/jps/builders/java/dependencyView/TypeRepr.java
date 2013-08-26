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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.asm4.Type;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * @author: db
 * Date: 14.02.11
 */
class TypeRepr {
  private static final byte PRIMITIVE_TYPE = 0x0;
  private static final byte CLASS_TYPE = 0x1;
  private static final byte ARRAY_TYPE = 0x2;

  private TypeRepr() {

  }

  interface AbstractType extends RW.Savable {
    AbstractType[] EMPTY_TYPE_ARRAY = new AbstractType[0];

    void updateClassUsages(DependencyContext context, int owner, Set<UsageRepr.Usage> s);
    String getDescr(DependencyContext context);
    void save(DataOutput out);
  }

  public static class PrimitiveType implements AbstractType {
    public final int type;

    @Override
    public String getDescr(final DependencyContext context) {
      return context.getValue(type);
    }

    @Override
    public void updateClassUsages(final DependencyContext context, final int owner, final Set<UsageRepr.Usage> s) {

    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(PRIMITIVE_TYPE);
        DataInputOutputUtil.writeINT(out, type);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    PrimitiveType(final int type) {
      this.type = type;
    }

    PrimitiveType(final DataInput in) {
      try {
        type = DataInputOutputUtil.readINT(in);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final PrimitiveType that = (PrimitiveType)o;

      return type == that.type;
    }

    @Override
    public int hashCode() {
      return type;
    }
  }

  public static class ArrayType implements AbstractType {
    public final AbstractType elementType;

    public AbstractType getDeepElementType() {
      AbstractType current = this;

      while (current instanceof ArrayType) {
        current = ((ArrayType)current).elementType;
      }

      return current;
    }

    @Override
    public String getDescr(final DependencyContext context) {
      return "[" + elementType.getDescr(context);
    }

    @Override
    public void updateClassUsages(final DependencyContext context, final int owner, final Set<UsageRepr.Usage> s) {
      elementType.updateClassUsages(context, owner, s);
    }

    ArrayType(final AbstractType elementType) {
      this.elementType = elementType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ArrayType arrayType = (ArrayType)o;

      return elementType.equals(arrayType.elementType);
    }

    @Override
    public int hashCode() {
      return elementType.hashCode();
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(ARRAY_TYPE);
        elementType.save(out);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static class ClassType implements AbstractType {
    public final int className;
    public final AbstractType[] typeArgs;

    @Override
    public String getDescr(final DependencyContext context) {
      return "L" + context.getValue(className) + ";";
    }

    @Override
    public void updateClassUsages(final DependencyContext context, final int owner, final Set<UsageRepr.Usage> s) {
      s.add(UsageRepr.createClassUsage(context, className));
    }

    ClassType(final int className) {
      this.className = className;
      typeArgs = EMPTY_TYPE_ARRAY;
    }

    ClassType(final DependencyContext context, final DataInput in) {
      try {
        className = DataInputOutputUtil.readINT(in);
        final int size = DataInputOutputUtil.readINT(in);
        if (size == 0) {
          typeArgs = EMPTY_TYPE_ARRAY;
        }
        else {
          typeArgs = new AbstractType[size];
          final DataExternalizer<AbstractType> externalizer = externalizer(context);
          for (int i = 0; i < size; i++) {
            typeArgs[i] = externalizer.read(in);
          }
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ClassType classType = (ClassType)o;

      if (className != classType.className) return false;
      if (!Arrays.equals(typeArgs, classType.typeArgs)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = className;
      result = 31 * result + (typeArgs != null ? Arrays.hashCode(typeArgs) : 0);
      return result;
    }

    @Override
    public void save(final DataOutput out) {
      try {
        out.writeByte(CLASS_TYPE);
        DataInputOutputUtil.writeINT(out, className);
        DataInputOutputUtil.writeINT(out, typeArgs.length);
        for (AbstractType t : typeArgs) {
          t.save(out);
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static Collection<AbstractType> createClassType(final DependencyContext context,
                                                         final String[] args,
                                                         final Collection<AbstractType> acc) {
    if (args != null) {
      for (String a : args) {
        acc.add(createClassType(context, context.get(a)));
      }
    }

    return acc;
  }

  public static ClassType createClassType(final DependencyContext context, final int s) {
    return (ClassType)context.getType(new ClassType(s));
  }

  public static AbstractType getType(final DependencyContext context, final int descr) {
    final Type t = Type.getType(context.getValue(descr));

    switch (t.getSort()) {
      case Type.OBJECT:
        return context.getType(new ClassType(context.get(StringUtil.replaceChar(t.getClassName(), '.', '/'))));

      case Type.ARRAY:
        return context.getType(new ArrayType(getType(context, t.getElementType())));

      default:
        return context.getType(new PrimitiveType(descr));
    }
  }

  public static AbstractType getType(final DependencyContext context, final Type t) {
    return getType(context, context.get(t.getDescriptor()));
  }

  public static AbstractType[] getType(final DependencyContext context, final Type[] t) {
    if(t.length == 0) return AbstractType.EMPTY_TYPE_ARRAY;
    final AbstractType[] r = new AbstractType[t.length];

    for (int i = 0; i < r.length; i++) {
      r[i] = getType(context, t[i]);
    }

    return r;
  }

  public static DataExternalizer<AbstractType> externalizer(final DependencyContext context) {
    return new DataExternalizer<AbstractType>() {
      @Override
      public void save(final DataOutput out, final AbstractType value) throws IOException {
        value.save(out);
      }

      @Override
      public AbstractType read(final DataInput in) throws IOException {
        AbstractType elementType;
        int level = 0;

        loop:
        while (true) {
          final byte tag = in.readByte();
          switch (tag) {
            case PRIMITIVE_TYPE:
              elementType = context.getType(new PrimitiveType(in));
              break loop;

            case CLASS_TYPE:
              elementType = context.getType(new ClassType(context, in));
              break loop;

            case ARRAY_TYPE:
              level++;
              break;

            default :
              System.out.println("Unknown type!");
          }
        }

        for (int i = 0; i < level; i++) {
          elementType = context.getType(new ArrayType(elementType));
        }

        return elementType;
      }
    };
  }
}
