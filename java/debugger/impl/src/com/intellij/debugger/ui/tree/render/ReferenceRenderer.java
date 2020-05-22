// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public abstract class ReferenceRenderer extends TypeRenderer {
  protected ReferenceRenderer() {
  }

  protected ReferenceRenderer(@NotNull String className) {
    super(className);
  }

  @Override
  public boolean isApplicable(Type type) {
    return type instanceof ReferenceType && DebuggerUtils.instanceOf(type, getClassName());
  }

  @Override
  public CompletableFuture<Boolean> isApplicableAsync(Type type) {
    if (type instanceof ReferenceType) {
      return DebuggerUtilsAsync.instanceOf(type, getClassName());
    }
    return CompletableFuture.completedFuture(false);
  }
}
