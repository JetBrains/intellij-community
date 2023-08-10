// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.javac.Iterators;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Method extends ProtoMember implements DiffCapable<Method, Method.Diff> {
  public final Iterable<TypeRepr> argTypes;
  private final Set<ParamAnnotation> paramAnnotations;
  private final Set<TypeRepr.ClassType> exceptions;

  public Method(
    JVMFlags flags, String signature, String name, String descriptor,
    @NotNull Iterable<TypeRepr.ClassType> annotations, Set<ParamAnnotation> parameterAnnotations,
    String[] exceptions, Object defaultValue) {

    super(flags, signature, name, TypeRepr.getType(Type.getReturnType(descriptor)), annotations, defaultValue);
    paramAnnotations = parameterAnnotations;
    this.exceptions = Iterators.collect(Iterators.map(Arrays.asList(exceptions), s -> new TypeRepr.ClassType(s)), new HashSet<>());
    argTypes = TypeRepr.getTypes(Type.getArgumentTypes(descriptor)); 
  }

  public Set<ParamAnnotation> getParamAnnotations() {
    return paramAnnotations;
  }

  public Set<TypeRepr.ClassType> getExceptions() {
    return exceptions;
  }
  // todo: need to additionally define normal equals-hashcode?
  @Override
  public boolean isSame(DiffCapable<?, ?> other) {
    if (!(other instanceof Method)) {
      return false;
    }
    Method that = (Method)other;
    return getName().equals(that.getName()) && getType().equals(that.getType()) && Iterators.equals(argTypes, that.argTypes);
  }

  @Override
  public int diffHashCode() {
    return 31 * (31 * Iterators.hashCode(argTypes) + getType().hashCode()) + getName().hashCode();
  }

  @Override
  public Method.Diff difference(Method other) {
    return new Diff(other);
  }

  public static class Diff implements Difference {

    public Diff(Method other) {
      // todo: diff necessary data
    }

    @Override
    public boolean unchanged() {
      return false;
    }

  }

}
