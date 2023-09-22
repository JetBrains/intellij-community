// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.diff.DiffCapable;

public final class JvmField extends ProtoMember implements DiffCapable<JvmField, JvmField.Diff> {

  public JvmField(JVMFlags flags, String signature, String name, String descriptor, @NotNull Iterable<TypeRepr.ClassType> annotations, Object value) {
    super(flags, signature, name, TypeRepr.getType(descriptor), annotations, value);
  }

  @Override
  public FieldUsage createUsage(String owner) {
    return new FieldUsage(owner, getName(), getType().getDescriptor());
  }

  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    return other instanceof JvmField && getName().equals(((JvmField)other).getName());
  }

  @Override
  public int diffHashCode() {
    return getName().hashCode();
  }

  @Override
  public JvmField.Diff difference(JvmField past) {
    return new Diff(past);
  }

  public final class Diff extends ProtoMember.Diff<JvmField> {

    public Diff(JvmField past) {
      super(past);
    }
  }

}
