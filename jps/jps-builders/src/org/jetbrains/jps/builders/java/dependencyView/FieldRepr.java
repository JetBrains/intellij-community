// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.Set;

final class FieldRepr extends ProtoMember implements ProtoFieldEntity {
  public void updateClassUsages(final DependencyContext context, final int owner, final Set<? super UsageRepr.Usage> s) {
    myType.updateClassUsages(context, owner, s);
  }

  FieldRepr(final DependencyContext context,
                   final int access,
                   final int name,
                   final int descriptor,
                   final int signature,
                   @NotNull
                   final Set<TypeRepr.ClassType> annotations, final Object value) {
    super(access, signature, name, TypeRepr.getType(context, descriptor), annotations, value);
  }

  FieldRepr(final DependencyContext context, final DataInput in) {
    super(context, in);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FieldRepr fieldRepr = (FieldRepr)o;

    return name == fieldRepr.name;
  }

  @Override
  public int hashCode() {
    return 31 * name;
  }

  public static DataExternalizer<FieldRepr> externalizer(final DependencyContext context) {
    return new DataExternalizer<FieldRepr>() {
      @Override
      public void save(@NotNull final DataOutput out, final FieldRepr value) {
        value.save(out);
      }

      @Override
      public FieldRepr read(@NotNull final DataInput in) {
        return new FieldRepr(context, in);
      }
    };
  }

  public UsageRepr.Usage createUsage(final DependencyContext context, final int owner) {
    return UsageRepr.createFieldUsage(context, name, owner, context.get(myType.getDescr(context)));
  }

  public UsageRepr.Usage createAssignUsage(final DependencyContext context, final int owner) {
    return UsageRepr.createFieldAssignUsage(context, name, owner, context.get(myType.getDescr(context)));
  }
}
