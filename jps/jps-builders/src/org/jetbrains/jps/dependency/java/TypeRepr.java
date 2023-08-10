// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.javac.Iterators;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.Collections;

public abstract class TypeRepr {

  public abstract @NotNull String getDescriptor();

  public abstract boolean equals(Object o);

  public abstract int hashCode();

  public static class PrimitiveType extends TypeRepr {

    @NotNull
    private final String myDescriptor;

    public PrimitiveType(String descriptor) {
      myDescriptor = descriptor;
    }

    @Override
    public @NotNull String getDescriptor() {
      return myDescriptor;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final PrimitiveType that = (PrimitiveType)o;

      if (!myDescriptor.equals(that.myDescriptor)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return myDescriptor.hashCode();
    }
  }

  public static class ClassType extends TypeRepr {

    private final String myJvmName;

    public ClassType(String jvmName) {
      myJvmName = jvmName;
    }

    @Override
    public @NotNull String getDescriptor() {
      return "L" + myJvmName + ";";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final ClassType classType = (ClassType)o;

      if (!myJvmName.equals(classType.myJvmName)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return myJvmName.hashCode();
    }
  }

  public static class ArrayType extends TypeRepr {
    @NotNull
    private final TypeRepr myElementType;

    public ArrayType(@NotNull TypeRepr elementType) {
      myElementType = elementType;
    }

    @Override
    public @NotNull String getDescriptor() {
      return "[" + myElementType.getDescriptor();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final ArrayType arrayType = (ArrayType)o;

      if (!myElementType.equals(arrayType.myElementType)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      return myElementType.hashCode();
    }
  }

  static Iterable<TypeRepr> getTypes(final Type[] types) {
    return types == null || types.length == 0? Collections.emptySet() : Iterators.map(Arrays.asList(types), t -> getType(t));
  }

  public static TypeRepr getType(final Type t) {
    return getType(t.getDescriptor());
  }

  public static TypeRepr getType(final String descriptor) {
    final Type t = Type.getType(descriptor);

    switch (t.getSort()) {
      case Type.OBJECT:
        return new ClassType(t.getClassName().replace('.', '/'));

      case Type.ARRAY:
        return new ArrayType(getType(t.getElementType().getDescriptor()));

      default: // todo: support 'method' type?
        return new PrimitiveType(descriptor);
    }
  }

}
