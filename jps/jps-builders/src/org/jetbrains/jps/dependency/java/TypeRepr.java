// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.javac.Iterators;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.Collections;

public abstract class TypeRepr {

  public abstract @NotNull String getDescriptor();

  public abstract boolean equals(Object o);

  public abstract int hashCode();

  public Iterable<Usage> getUsages() {
    return Collections.emptyList();
  }

  public static final class PrimitiveType extends TypeRepr {

    private final @NotNull String myDescriptor;

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

  public static final class ClassType extends TypeRepr {

    private final String myJvmName;

    public ClassType(String jvmName) {
      myJvmName = jvmName;
    }

    public String getJvmName() {
      return myJvmName;
    }

    @Override
    public Iterable<Usage> getUsages() {
      return Collections.singleton(new ClassUsage(myJvmName));
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

  public static final class ArrayType extends TypeRepr {
    private final @NotNull TypeRepr myElementType;

    public ArrayType(@NotNull TypeRepr elementType) {
      myElementType = elementType;
    }

    @Override
    public @NotNull String getDescriptor() {
      return "[" + myElementType.getDescriptor();
    }

    public @NotNull TypeRepr getElementType() {
      return myElementType;
    }

    public @NotNull TypeRepr getDeepElementType() {
      TypeRepr current = this;
      while (current instanceof ArrayType) {
        current = ((ArrayType)current).myElementType;
      }
      return current;
    }

    @Override
    public Iterable<Usage> getUsages() {
      return getDeepElementType().getUsages();
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
