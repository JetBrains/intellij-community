// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.NotNull;

/**
 * A type that represents concrete JVM primitive type or subset of values of given type
 */
public interface DfPrimitiveType extends DfType {
  @NotNull
  PsiPrimitiveType getPsiType();
}
