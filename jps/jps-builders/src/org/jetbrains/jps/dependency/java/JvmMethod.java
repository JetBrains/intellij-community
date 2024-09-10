// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.impl.RW;
import org.jetbrains.jps.javac.Iterators;
import org.jetbrains.org.objectweb.asm.Type;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class JvmMethod extends ProtoMember implements DiffCapable<JvmMethod, JvmMethod.Diff> {
  private final Iterable<TypeRepr> myArgTypes;
  private final Iterable<ParamAnnotation> myParamAnnotations;
  private final Iterable<TypeRepr.ClassType> myExceptions;

  public JvmMethod(
    JVMFlags flags, String signature, String name, String descriptor,
    @NotNull Iterable<ElementAnnotation> annotations, Iterable<ParamAnnotation> parameterAnnotations,
    Iterable<String> exceptions, Object defaultValue) {

    super(flags, signature, name, TypeRepr.getType(Type.getReturnType(descriptor)), annotations, defaultValue);
    myParamAnnotations = parameterAnnotations;
    myExceptions = Iterators.collect(Iterators.map(exceptions, s -> new TypeRepr.ClassType(s)), new SmartList<>());
    myArgTypes = TypeRepr.getTypes(Type.getArgumentTypes(descriptor));
  }

  public JvmMethod(GraphDataInput in) throws IOException {
    super(in);
    myArgTypes = RW.readCollection(in, () -> TypeRepr.getType(in.readUTF()));
    myParamAnnotations = RW.readCollection(in, () -> new ParamAnnotation(in));
    myExceptions = RW.readCollection(in, () -> new TypeRepr.ClassType(in.readUTF()));
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    super.write(out);
    RW.writeCollection(out, myArgTypes, t -> out.writeUTF(t.getDescriptor()));
    RW.writeCollection(out, myParamAnnotations, pa -> pa.write(out));
    RW.writeCollection(out, myExceptions, t -> out.writeUTF(t.getJvmName()));
  }

  public boolean isConstructor() {
    return "<init>".equals(getName());
  }

  public boolean isStaticInitializer() {
    return "<clinit>".equals(getName());
  }

  public boolean isOverridable() {
    return !isFinal() && !isStatic() && !isPrivate() && !isConstructor();
  }
  
  @Override
  public MethodUsage createUsage(JvmNodeReferenceID owner) {
    return new MethodUsage(owner, getName(), getDescriptor());
  }

  public Predicate<Node<?, ?>> createUsageQuery(JvmNodeReferenceID owner) {
    String thisMethodName = getName();
    return n -> Iterators.find(n.getUsages(), u -> u instanceof MethodUsage && owner.equals(u.getElementOwner()) && Objects.equals(((MethodUsage)u).getName(), thisMethodName)) != null;
  }

  public Iterable<ParamAnnotation> getParamAnnotations() {
    return myParamAnnotations;
  }

  public Iterable<TypeRepr.ClassType> getExceptions() {
    return myExceptions;
  }

  public Iterable<TypeRepr> getArgTypes() {
    return myArgTypes;
  }

  public boolean isSameByJavaRules(JvmMethod other) {
    return getName().equals(other.getName()) && Iterators.equals(myArgTypes, other.myArgTypes);
  }

  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    if (!(other instanceof JvmMethod)) {
      return false;
    }
    JvmMethod that = (JvmMethod)other;
    return Objects.equals(getType(), that.getType()) && isSameByJavaRules(that);
  }

  @Override
  public int diffHashCode() {
    return 31 * (31 * Iterators.hashCode(myArgTypes) + getType().hashCode()) + getName().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof JvmMethod && isSame((JvmMethod)obj);
  }

  @Override
  public int hashCode() {
    return diffHashCode();
  }

  @Override
  public JvmMethod.Diff difference(JvmMethod past) {
    return new Diff(past);
  }

  public final class Diff extends ProtoMember.Diff<JvmMethod> {
    private final Supplier<Specifier<ParamAnnotation, ParamAnnotation.Diff>> myParamAnnotationsDiff;
    private final Supplier<Specifier<TypeRepr.ClassType, ?>> myExceptionsDiff;

    public Diff(JvmMethod past) {
      super(past);
      myParamAnnotationsDiff = Utils.lazyValue(() -> Difference.deepDiff(myPast.getParamAnnotations(), getParamAnnotations()));
      myExceptionsDiff = Utils.lazyValue(() -> Difference.diff(myPast.getExceptions(), getExceptions()));
    }

    @Override
    public boolean unchanged() {
      return super.unchanged() && paramAnnotations().unchanged() && exceptions().unchanged();
    }

    public Specifier<ParamAnnotation, ParamAnnotation.Diff> paramAnnotations() {
      return myParamAnnotationsDiff.get();
    }

    public Specifier<TypeRepr.ClassType, ?> exceptions() {
      return myExceptionsDiff.get();
    }
  }

  public String getDescriptor() {
    return getDescriptor(myArgTypes, getType());
  }

  public static @NotNull String getDescriptor(Iterable<TypeRepr> argTypes, @NotNull TypeRepr returnType) {
    final StringBuilder buf = new StringBuilder();

    buf.append("(");

    for (TypeRepr t : argTypes) {
      buf.append(t.getDescriptor());
    }

    buf.append(")");
    buf.append(returnType.getDescriptor());

    return buf.toString();
  }

  @Override
  public String toString() {
    return getName() + getDescriptor();
  }
}
