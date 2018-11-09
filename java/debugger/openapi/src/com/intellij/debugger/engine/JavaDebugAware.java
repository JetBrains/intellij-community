// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public abstract class JavaDebugAware {
  static final ExtensionPointName<JavaDebugAware> EP_NAME = ExtensionPointName.create("com.intellij.debugger.javaDebugAware");

  public abstract boolean isBreakpointAware(@NotNull PsiFile psiFile);
}