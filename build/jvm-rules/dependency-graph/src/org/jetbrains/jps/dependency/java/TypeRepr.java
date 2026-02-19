// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.util.Iterators;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.Collections;

public abstract class TypeRepr {

  public abstract @NotNull String getDescriptor();

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  public Iterable<Usage> getUsages() {
    return Collections.emptyList();
  }

  public static final class PrimitiveType extends TypeRepr {
    public static final PrimitiveType BOOLEAN = new PrimitiveType("Z");
    public static final PrimitiveType BYTE = new PrimitiveType("B");
    public static final PrimitiveType CHAR = new PrimitiveType("C");
    public static final PrimitiveType FLOAT = new PrimitiveType("F");
    public static final PrimitiveType INT = new PrimitiveType("I");
    public static final PrimitiveType LONG = new PrimitiveType("J");
    public static final PrimitiveType SHORT = new PrimitiveType("S");
    public static final PrimitiveType DOUBLE = new PrimitiveType("D");

    private final @NotNull String myDescriptor;

    public PrimitiveType(@NotNull String descriptor) {
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
    public static final ClassType BOOLEAN = new ClassType("java/lang/Boolean");
    public static final ClassType BYTE = new ClassType("java/lang/Byte");
    public static final ClassType CHARACTER = new ClassType("java/lang/Character");
    public static final ClassType FLOAT = new ClassType("java/lang/Float");
    public static final ClassType INTEGER = new ClassType("java/lang/Integer");
    public static final ClassType LONG = new ClassType("java/lang/Long");
    public static final ClassType SHORT = new ClassType("java/lang/Short");
    public static final ClassType DOUBLE = new ClassType("java/lang/Double");

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

  public static TypeRepr getType(final String descriptor) {
    return getType(Type.getType(descriptor));
  }

  public static TypeRepr getType(final Type t) {
    switch (t.getSort()) {
      case Type.OBJECT:
        return new ClassType(t.getClassName().replace('.', '/'));

      case Type.ARRAY:
        return new ArrayType(getType(t.getElementType().getDescriptor()));

      default: // todo: support 'method' type?
        return new PrimitiveType(t.getDescriptor());
    }
  }
}
