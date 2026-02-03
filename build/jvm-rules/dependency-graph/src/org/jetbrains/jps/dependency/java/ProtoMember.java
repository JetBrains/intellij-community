// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;

import java.io.IOException;
import java.util.Objects;

public abstract class ProtoMember extends Proto {
  private final @NotNull TypeRepr type;
  private final @Nullable Object value;

  public ProtoMember(JVMFlags flags, String signature, String name, @NotNull TypeRepr type, @NotNull Iterable<ElementAnnotation> annotations, @Nullable Object value) {
    super(flags, signature, name, annotations);
    this.type = type;
    this.value = value;
  }

  public ProtoMember(GraphDataInput in) throws IOException {
    super(in);
    type = TypeRepr.getType(in.readUTF());
    value = JvmProtoMemberValueExternalizer.read(in);
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    super.write(out);
    out.writeUTF(type.getDescriptor());
    JvmProtoMemberValueExternalizer.write(out, value);
  }

  public abstract MemberUsage createUsage(JvmNodeReferenceID owner);

  public @NotNull TypeRepr getType() {
    return type;
  }

  public @Nullable Object getValue() {
    return value;
  }

  public class Diff<V extends ProtoMember> extends Proto.Diff<V> {

    public Diff(V past) {
      super(past);
    }

    @Override
    public boolean unchanged() {
      return super.unchanged() && !typeChanged() && !valueChanged();
    }

    public boolean typeChanged() {
      return !Objects.equals(myPast.getType(), getType());
    }

    public boolean valueChanged() {
      return !Objects.deepEquals(myPast.getValue(), getValue());
    }

    public boolean valueAdded() {
      return myPast.getValue() == null && getValue() != null;
    }

    public boolean valueRemoved() {
      return myPast.getValue() != null && getValue() == null;
    }
  }
}
