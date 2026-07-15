// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.CurrentMethodReturnValueDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.MethodReturnValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.sun.jdi.Method;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CurrentMethodReturnValueData extends MethodReturnValueData {

  public CurrentMethodReturnValueData(@NotNull Method method, @Nullable Value returnValue) {
    super(method, returnValue);
  }

  @Override
  protected MethodReturnValueDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new CurrentMethodReturnValueDescriptorImpl(project, getMethod(), getReturnValue());
  }

  @Override
  public DisplayKey<MethodReturnValueDescriptorImpl> getDisplayKey() {
    return new CurrentMethodReturnValueDisplayKey(getMethod(), getReturnValue());
  }

  /**
   * A display key distinct from the superclass key, so the current method return value node
   * does not inherit the last method return value node's display state.
   * The key must not extend {@link Pair}, because {@link Pair#equals(Object)} does not distinguish subclasses.
   */
  private record CurrentMethodReturnValueDisplayKey(@NotNull Method method, @Nullable Value value)
    implements DisplayKey<MethodReturnValueDescriptorImpl> {
  }
}
