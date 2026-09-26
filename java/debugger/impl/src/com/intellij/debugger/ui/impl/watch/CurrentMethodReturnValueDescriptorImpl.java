// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Method;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

public final class CurrentMethodReturnValueDescriptorImpl extends MethodReturnValueDescriptorImpl {
  public CurrentMethodReturnValueDescriptorImpl(Project project, @NotNull Method method, Value value) {
    super(project, method, value);
  }

  @Override
  public String getName() {
    return JavaDebuggerBundle.message("label.current.method.return.value");
  }
}
