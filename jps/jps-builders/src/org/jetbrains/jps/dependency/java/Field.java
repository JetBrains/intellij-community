// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;

public class Field extends ProtoMember implements DiffCapable<Field, Field.Diff> {

  public Field(JVMFlags flags, String signature, String name, String descriptor, @NotNull Iterable<TypeRepr.ClassType> annotations, Object value) {
    super(flags, signature, name, TypeRepr.getType(descriptor), annotations, value);
  }

  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    return other instanceof Field && getName().equals(((Field)other).getName());
  }

  @Override
  public int diffHashCode() {
    return getName().hashCode();
  }

  @Override
  public Field.Diff difference(Field other) {
    return new Diff(other);
  }

  public static class Diff implements Difference {

    public Diff(Field other) {
      // todo: diff necessary data
    }

    @Override
    public boolean unchanged() {
      return false;
    }
  }

}
