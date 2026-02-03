// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.compiler.ex;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import org.jetbrains.annotations.NotNull;

public interface CompileContextEx extends CompileContext {
  void addMessage(@NotNull CompilerMessage message);

  void addScope(@NotNull CompileScope additionalScope);
}
