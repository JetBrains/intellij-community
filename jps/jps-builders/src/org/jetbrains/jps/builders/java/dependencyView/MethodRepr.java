/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.org.objectweb.asm.Type;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

/**
 * @author: db
 */
class MethodRepr extends ProtoMember {

  interface Predicate {
    boolean satisfy(MethodRepr m);
  }

  public final Set<ParamAnnotation> myParameterAnnotations;
  public final TypeRepr.AbstractType[] myArgumentTypes;
  public final Set<TypeRepr.AbstractType> myExceptions;

  public static abstract class Diff extends DifferenceImpl {

    Diff(@NotNull Difference delegate) {
      super(delegate);
    }

    public abstract Specifier<ParamAnnotation, Difference> parameterAnnotations();

    public abstract Specifier<TypeRepr.AbstractType, Difference> exceptions();

    public abstract boolean defaultAdded();

    public abstract boolean defaultRemoved();
  }

  @Override
  public Diff difference(final Proto past) {
    final MethodRepr m = (MethodRepr)past;
    final Difference diff = super.difference(past);
    final Difference.Specifier<TypeRepr.AbstractType, Difference> excs = Difference.make(m.myExceptions, myExceptions);
    final Difference.Specifier<ParamAnnotation, Difference> paramAnnotations = Difference.make(m.myParameterAnnotations, myParameterAnnotations);
    final int base = paramAnnotations.unchanged()? diff.base() : diff.base() | Difference.ANNOTATIONS;

    return new Diff(diff) {
      @Override
      public Specifier<ParamAnnotation, Difference> parameterAnnotations() {
        return paramAnnotations;
      }

      @Override
      public boolean no() {
        return base() == NONE && !defaultAdded() && !defaultRemoved() && excs.unchanged() && paramAnnotations.unchanged();
      }

      @Override
      public boolean defaultAdded() {
        return hasValue() && !m.hasValue();
      }

      @Override
      public boolean defaultRemoved() {
        return !hasValue() && m.hasValue();
      }

      @Override
      public Specifier<TypeRepr.AbstractType, Difference> exceptions() {
        return excs;
      }

      @Override
      public int base() {
        return base;
      }

      @Override
      public boolean hadValue() {
        return m.hasValue();
      }
    };
  }

  public void updateClassUsages(final DependencyContext context, final int owner, final Set<UsageRepr.Usage> s) {
    myType.updateClassUsages(context, owner, s);

    for (final TypeRepr.AbstractType argType : myArgumentTypes) {
      argType.updateClassUsages(context, owner, s);
    }

    if (myExceptions != null) {
      for (TypeRepr.AbstractType typ : myExceptions) {
        typ.updateClassUsages(context, owner, s);
      }
    }
  }

  public MethodRepr(final DependencyContext context,
                    final int accessFlags,
                    final int name,
                    final int signature,
                    final String descriptor,
                    final Set<TypeRepr.ClassType> annotations, Set<ParamAnnotation> parameterAnnotations, final String[] exceptions,
                    final Object defaultValue) {
    super(accessFlags, signature, name, TypeRepr.getType(context, Type.getReturnType(descriptor)), annotations, defaultValue);
    myParameterAnnotations = parameterAnnotations;
    Set<TypeRepr.AbstractType> typeCollection =
      exceptions != null ? new THashSet<>(exceptions.length) : Collections.emptySet();
    myExceptions = (Set<TypeRepr.AbstractType>)TypeRepr.createClassType(context, exceptions, typeCollection);
    myArgumentTypes = TypeRepr.getType(context, Type.getArgumentTypes(descriptor));
  }

  public MethodRepr(final DependencyContext context, final DataInput in) {
    super(context, in);
    try {
      final DataExternalizer<TypeRepr.AbstractType> externalizer = TypeRepr.externalizer(context);

      final int size = DataInputOutputUtil.readINT(in);
      myArgumentTypes = RW.read(externalizer, in, new TypeRepr.AbstractType[size]);

      myExceptions = RW.read(externalizer, new THashSet<>(0), in);

      final DataExternalizer<TypeRepr.ClassType> clsTypeExternalizer = TypeRepr.classTypeExternalizer(context);
      myParameterAnnotations = RW.read(new DataExternalizer<ParamAnnotation>() {
        @Override
        public void save(@NotNull DataOutput out, ParamAnnotation value) throws IOException {
          value.save(out);
        }
        @Override
        public ParamAnnotation read(@NotNull DataInput in) throws IOException {
          return new ParamAnnotation(clsTypeExternalizer, in);
        }
      }, new THashSet<>(), in);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void save(final DataOutput out) {
    super.save(out);
    RW.save(myArgumentTypes, out);
    RW.save(myExceptions, out);
    RW.save(myParameterAnnotations, out);
  }

  public static DataExternalizer<MethodRepr> externalizer(final DependencyContext context) {
    return new DataExternalizer<MethodRepr>() {
      @Override
      public void save(@NotNull final DataOutput out, final MethodRepr value) throws IOException {
        value.save(out);
      }

      @Override
      public MethodRepr read(@NotNull DataInput in) throws IOException {
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
    return UsageRepr.createMetaMethodUsage(context, name, owner);
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
    Arrays.sort(es, Comparator.comparing(o -> o.getDescr(context)));
    stream.print("          Exceptions : ");
    for (final TypeRepr.AbstractType e : es) {
      stream.print(e.getDescr(context));
      stream.print("; ");
    }
    stream.println();
  }
}
