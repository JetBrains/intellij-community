/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.MethodReturnValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Method;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MethodReturnValueData extends DescriptorData<MethodReturnValueDescriptorImpl>{
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

  protected MethodReturnValueDescriptorImpl createDescriptorImpl(Project project) {
    return new MethodReturnValueDescriptorImpl(project, myMethod, myReturnValue);
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MethodReturnValueData that = (MethodReturnValueData)o;

    if (!myMethod.equals(that.myMethod)) return false;
    if (myReturnValue != null ? !myReturnValue.equals(that.myReturnValue) : that.myReturnValue != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myReturnValue != null ? myReturnValue.hashCode() : 0);
    result = 31 * result + myMethod.hashCode();
    return result;
  }

  public DisplayKey<MethodReturnValueDescriptorImpl> getDisplayKey() {
    return new MethodReturnValueDisplayKey(myMethod, myReturnValue);
  }

  private static final class MethodReturnValueDisplayKey implements DisplayKey<MethodReturnValueDescriptorImpl> {
    private final @NotNull Method myMethod;
    private final @Nullable Value myValue;

    public MethodReturnValueDisplayKey(@NotNull Method method, @Nullable Value value) {
      myMethod = method;
      myValue = value;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MethodReturnValueDisplayKey that = (MethodReturnValueDisplayKey)o;

      if (!myMethod.equals(that.myMethod)) return false;
      if (myValue != null ? !myValue.equals(that.myValue) : that.myValue != null) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = myMethod.hashCode();
      result = 31 * result + (myValue != null ? myValue.hashCode() : 0);
      return result;
    }
  }
}
