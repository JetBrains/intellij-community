// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.Objects;

public final class JvmField extends ProtoMember implements DiffCapable<JvmField, JvmField.Diff> {
  private static final JVMFlags INLINABLE_FIELD_FLAGS = new JVMFlags(Opcodes.ACC_FINAL);
  
  public JvmField(JVMFlags flags, String signature, String name, String descriptor, @NotNull Iterable<ElementAnnotation> annotations, Object value) {
    super(flags, signature, name, TypeRepr.getType(descriptor), annotations, value);
  }

  public JvmField(GraphDataInput in) throws IOException {
    super(in);
  }

  public boolean isSameKind(JvmField other) {
    return isStatic() == other.isStatic() && isSynthetic() == other.isSynthetic() && isFinal() == other.isFinal() && Objects.equals(getType(), other.getType());
  }

  public boolean isInlinable() {
    return getFlags().isAllSet(INLINABLE_FIELD_FLAGS);
  }

  @Override
  public FieldUsage createUsage(JvmNodeReferenceID owner) {
    return new FieldUsage(owner, getName(), getType().getDescriptor());
  }

  public FieldAssignUsage createAssignUsage(String owner) {
    return new FieldAssignUsage(owner, getName(), getType().getDescriptor());
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
  public int hashCode() {
    return 31 * diffHashCode() + getType().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof JvmField)) {
      return false;
    }
    JvmField other = (JvmField)obj;
    return isSame(other) && Objects.equals(getType(), other.getType());
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

  @Override
  public String toString() {
    return getName() + getType().getDescriptor();
  }
}
