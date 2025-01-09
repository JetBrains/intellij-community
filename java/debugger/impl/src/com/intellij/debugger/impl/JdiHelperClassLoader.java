// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.sun.jdi.ClassType;
import org.jetbrains.annotations.Nullable;

public interface JdiHelperClassLoader {
  ExtensionPointName<JdiHelperClassLoader> EP_NAME =
    ExtensionPointName.create("com.intellij.debugger.jdiClassLoader");

  @Nullable ClassType getHelperClass(Class<?> cls, EvaluationContextImpl evaluationContext,
                                     String... additionalClassesToLoad) throws EvaluateException;
}
