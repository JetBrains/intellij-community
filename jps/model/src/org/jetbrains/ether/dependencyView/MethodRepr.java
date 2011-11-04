package org.jetbrains.ether.dependencyView;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.ether.RW;
import org.objectweb.asm.Type;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 01.02.11
 * Time: 5:03
 * To change this template use File | Settings | File Templates.
 */
class MethodRepr extends ProtoMember {
  private static TypeRepr.AbstractType[] dummyAbstractType = new TypeRepr.AbstractType[0];

  public final TypeRepr.AbstractType[] argumentTypes;
  public final Set<TypeRepr.AbstractType> exceptions;

  public abstract class Diff extends Difference {
    public abstract Specifier<TypeRepr.AbstractType> exceptions();

    public abstract boolean defaultAdded();

    public abstract boolean defaultRemoved();
  }

  @Override
  public Difference difference(final Proto past) {
    final Difference diff = super.difference(past);
    final Difference.Specifier<TypeRepr.AbstractType> excs = Difference.make(((MethodRepr)past).exceptions, exceptions);

    return new Diff() {
      @Override
      public int addedModifiers() {
        return diff.addedModifiers();
      }

      @Override
      public int removedModifiers() {
        return diff.removedModifiers();
      }

      @Override
      public boolean no() {
        return base() == NONE && !defaultAdded() && !defaultRemoved() && excs.unchanged();
      }

      @Override
      public boolean defaultAdded() {
        return hasValue() && !((MethodRepr)past).hasValue();
      }

      @Override
      public boolean defaultRemoved() {
        return !hasValue() && ((MethodRepr)past).hasValue();
      }

      @Override
      public Specifier<TypeRepr.AbstractType> exceptions() {
        return excs;
      }

      @Override
      public int base() {
        return diff.base();
      }

      @Override
      public boolean packageLocalOn() {
        return diff.packageLocalOn();
      }
    };
  }

  public void updateClassUsages(final DependencyContext context, final DependencyContext.S owner, final UsageRepr.Cluster s) {
    type.updateClassUsages(context, owner, s);

    for (int i = 0; i < argumentTypes.length; i++) {
      argumentTypes[i].updateClassUsages(context, owner, s);
    }

    if (exceptions != null) {
      for (TypeRepr.AbstractType typ : exceptions) {
        typ.updateClassUsages(context, owner, s);
      }
    }
  }

  public MethodRepr(final DependencyContext context,
                    final int a,
                    final DependencyContext.S n,
                    final DependencyContext.S s,
                    final String d,
                    final String[] e,
                    final Object value) {
    super(a, s, n, TypeRepr.getType(context, Type.getReturnType(d)), value);
    exceptions = (Set<TypeRepr.AbstractType>)TypeRepr.createClassType(context, e, new HashSet<TypeRepr.AbstractType>());
    argumentTypes = TypeRepr.getType(context, Type.getArgumentTypes(d));
  }

  public MethodRepr(final DependencyContext context, final DataInput in) {
    super(context, in);
    try {
      final DataExternalizer<TypeRepr.AbstractType> externalizer = TypeRepr.externalizer(context);
      argumentTypes = RW.read(externalizer, in, new TypeRepr.AbstractType[in.readInt()]);
      exceptions = (Set<TypeRepr.AbstractType>)RW.read(externalizer, new HashSet<TypeRepr.AbstractType>(), in);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public MethodRepr(final DependencyContext context, final BufferedReader r) {
    super(context, r);
    argumentTypes = RW.readMany(r, TypeRepr.reader(context), new ArrayList<TypeRepr.AbstractType>()).toArray(dummyAbstractType);
    exceptions = (Set<TypeRepr.AbstractType>)RW.readMany(r, TypeRepr.reader(context), new HashSet<TypeRepr.AbstractType>());
  }

  public void save(final DataOutput out) {
    super.save(out);
    RW.save(argumentTypes, out);
    RW.save(exceptions, out);
  }

  public void write(final BufferedWriter w) {
    super.write(w);
    RW.writeln(w, argumentTypes, TypeRepr.fromAbstractType);
    RW.writeln(w, exceptions);
  }

  public static RW.Reader<MethodRepr> reader(final DependencyContext context) {
    return new RW.Reader<MethodRepr>() {
      public MethodRepr read(final BufferedReader r) {
        return new MethodRepr(context, r);
      }
    };
  }

  public static DataExternalizer<MethodRepr> externalizer(final DependencyContext context) {
    return new DataExternalizer<MethodRepr>() {
      @Override
      public void save(final DataOutput out, final MethodRepr value) throws IOException {
        value.save(out);
      }

      @Override
      public MethodRepr read(DataInput in) throws IOException {
        return new MethodRepr(context, in);
      }
    };
  }

  ;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MethodRepr that = (MethodRepr)o;

    return name.equals(that.name) && type.equals(that.type) && Arrays.equals(argumentTypes, that.argumentTypes);
  }

  @Override
  public int hashCode() {
    return 31 * (31 * Arrays.hashCode(argumentTypes) + type.hashCode()) + name.hashCode();
  }

  public UsageRepr.Usage createUsage(final DependencyContext context, final DependencyContext.S owner) {
    final StringBuilder buf = new StringBuilder();

    buf.append("(");

    for (TypeRepr.AbstractType t : argumentTypes) {
      buf.append(t.getDescr(context));
    }

    buf.append(")");
    buf.append(type.getDescr(context));

    return UsageRepr.createMethodUsage(context, name, owner, buf.toString());
  }
}
