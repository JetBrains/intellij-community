// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.MethodReturnValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.sun.jdi.Method;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class MethodReturnValueData extends DescriptorData<MethodReturnValueDescriptorImpl> {
  private final @Nullable Value myReturnValue;
  private final @NotNull Method myMethod;

  public MethodReturnValueData(@NotNull Method method, @Nullable Value returnValue) {
    super();
    myMethod = method;
    myReturnValue = returnValue;
  }

  public @Nullable Value getReturnValue() {
    return myReturnValue;
  }

  public @NotNull Method getMethod() {
    return myMethod;
  }

  @Override
  protected MethodReturnValueDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new MethodReturnValueDescriptorImpl(project, myMethod, myReturnValue);
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MethodReturnValueData that = (MethodReturnValueData)o;

    if (!myMethod.equals(that.myMethod)) return false;
    if (!Objects.equals(myReturnValue, that.myReturnValue)) return false;

    return true;
  }

  public int hashCode() {
    return Objects.hash(myReturnValue, myMethod);
  }

  @Override
  public DisplayKey<MethodReturnValueDescriptorImpl> getDisplayKey() {
    return new MethodReturnValueDisplayKey(myMethod, myReturnValue);
  }

  private static final class MethodReturnValueDisplayKey
    extends Pair<Method, Value> implements DisplayKey<MethodReturnValueDescriptorImpl> {
    MethodReturnValueDisplayKey(@NotNull Method method, @Nullable Value value) {
      super(method, value);
    }
  }
}
