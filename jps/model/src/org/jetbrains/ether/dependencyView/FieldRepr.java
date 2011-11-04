package org.jetbrains.ether.dependencyView;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.ether.RW;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 4:56
 * To change this template use File | Settings | File Templates.
 */
class FieldRepr extends ProtoMember {
  public void updateClassUsages(final DependencyContext context, final DependencyContext.S owner, final UsageRepr.Cluster s) {
    type.updateClassUsages(context, owner, s);
  }

  public FieldRepr(final DependencyContext context,
                   final int a,
                   final DependencyContext.S n,
                   final DependencyContext.S d,
                   final DependencyContext.S s,
                   final Object v) {
    super(a, s, n, TypeRepr.getType(context, d), v);
  }

  public FieldRepr(final DependencyContext context, final DataInput in) {
    super(context, in);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FieldRepr fieldRepr = (FieldRepr)o;

    return name.equals(fieldRepr.name);
  }

  @Override
  public int hashCode() {
    return 31 * name.hashCode();
  }

  public static DataExternalizer<FieldRepr> externalizer(final DependencyContext context) {
    return new DataExternalizer<FieldRepr>() {
      @Override
      public void save(final DataOutput out, final FieldRepr value) throws IOException {
        value.save(out);
      }

      @Override
      public FieldRepr read(final DataInput in) throws IOException {
        return new FieldRepr(context, in);
      }
    };
  }

  public UsageRepr.Usage createUsage(final DependencyContext context, final DependencyContext.S owner) {
    return UsageRepr.createFieldUsage(context, name, owner, context.get(type.getDescr(context)));
  }

  public UsageRepr.Usage createAssignUsage(final DependencyContext context, final DependencyContext.S owner) {
    return UsageRepr.createFieldAssignUsage(context, name, owner, context.get(type.getDescr(context)));
  }
}
