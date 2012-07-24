package org.jetbrains.ether.dependencyView;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.asm4.Type;
import org.jetbrains.ether.RW;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Comparator;
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
  interface Predicate {
    boolean satisfy(MethodRepr m);
  }

  public final TypeRepr.AbstractType[] myArgumentTypes;
  public final Set<TypeRepr.AbstractType> myExceptions;

  public abstract class Diff extends Difference {
    public abstract Specifier<TypeRepr.AbstractType> exceptions();

    public abstract boolean defaultAdded();

    public abstract boolean defaultRemoved();
  }

  @Override
  public Difference difference(final Proto past) {
    final Difference diff = super.difference(past);
    final Difference.Specifier<TypeRepr.AbstractType> excs = Difference.make(((MethodRepr)past).myExceptions, myExceptions);

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

      @Override
      public boolean hadValue() {
        return ((MethodRepr)past).hasValue();
      }

      @Override
      public boolean weakedAccess() {
        return diff.weakedAccess();
      }
    };
  }

  public void updateClassUsages(final DependencyContext context, final int owner, final Set<UsageRepr.Usage> s) {
    myType.updateClassUsages(context, owner, s);

    for (int i = 0; i < myArgumentTypes.length; i++) {
      myArgumentTypes[i].updateClassUsages(context, owner, s);
    }

    if (myExceptions != null) {
      for (TypeRepr.AbstractType typ : myExceptions) {
        typ.updateClassUsages(context, owner, s);
      }
    }
  }

  public MethodRepr(final DependencyContext context,
                    final int a,
                    final int n,
                    final int s,
                    final String d,
                    final String[] e,
                    final Object value) {
    super(a, s, n, TypeRepr.getType(context, Type.getReturnType(d)), value);
    myExceptions = (Set<TypeRepr.AbstractType>)TypeRepr.createClassType(context, e, new HashSet<TypeRepr.AbstractType>());
    myArgumentTypes = TypeRepr.getType(context, Type.getArgumentTypes(d));
  }

  public MethodRepr(final DependencyContext context, final DataInput in) {
    super(context, in);
    try {
      final DataExternalizer<TypeRepr.AbstractType> externalizer = TypeRepr.externalizer(context);
      final int size = in.readInt();
      myArgumentTypes = RW.read(externalizer, in, new TypeRepr.AbstractType[size]);
      myExceptions = (Set<TypeRepr.AbstractType>)RW.read(externalizer, new HashSet<TypeRepr.AbstractType>(), in);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void save(final DataOutput out) {
    super.save(out);
    RW.save(myArgumentTypes, out);
    RW.save(myExceptions, out);
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

  static Predicate equalByJavaRules(final MethodRepr me) {
    return new Predicate() {
      @Override
      public boolean satisfy(MethodRepr that) {
        if (me == that) return true;
        return me.name == that.name && Arrays.equals(me.myArgumentTypes, that.myArgumentTypes);
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MethodRepr that = (MethodRepr)o;

    return name == that.name && myType.equals(that.myType) && Arrays.equals(myArgumentTypes, that.myArgumentTypes);
  }

  @Override
  public int hashCode() {
    return 31 * (31 * Arrays.hashCode(myArgumentTypes) + myType.hashCode()) + name;
  }

  private String getDescr(final DependencyContext context) {
    final StringBuilder buf = new StringBuilder();

    buf.append("(");

    for (TypeRepr.AbstractType t : myArgumentTypes) {
      buf.append(t.getDescr(context));
    }

    buf.append(")");
    buf.append(myType.getDescr(context));

    return buf.toString();
  }

  public UsageRepr.Usage createUsage(final DependencyContext context, final int owner) {
    return UsageRepr.createMethodUsage(context, name, owner, getDescr(context));
  }

  public UsageRepr.Usage createMetaUsage(final DependencyContext context, final int owner) {
    return UsageRepr.createMetaMethodUsage(context, name, owner, getDescr(context));
  }

  @Override
  public void toStream(final DependencyContext context, final PrintStream stream) {
    super.toStream(context, stream);
    stream.print("          Arguments  : ");
    for (TypeRepr.AbstractType t : myArgumentTypes) {
      stream.print(t.getDescr(context));
      stream.print("; ");
    }
    stream.println();

    final TypeRepr.AbstractType[] es = myExceptions.toArray(new TypeRepr.AbstractType[myExceptions.size()]);
    Arrays.sort(es, new Comparator<TypeRepr.AbstractType>() {
          @Override
          public int compare(final TypeRepr.AbstractType o1, final TypeRepr.AbstractType o2) {
            return o1.getDescr(context).compareTo(o2.getDescr(context));
          }
        });
    stream.print("          Exceptions : ");
    for (final TypeRepr.AbstractType e : es) {
      stream.print(e.getDescr(context));
      stream.print("; ");
    }
    stream.println();
  }
}
