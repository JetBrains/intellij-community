// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * A type that represents concrete JVM primitive type or subset of values of given type
 */
public interface DfPrimitiveType extends DfType {
  @NotNull
  PsiPrimitiveType getPsiType();

  /**
   * Cast this type to the specified primitive type 
   * @param type target type
   * @return result of the cast
   */
  default @NotNull DfType castTo(@NotNull PsiPrimitiveType type) {
    Object value = TypeConversionUtil.computeCastTo(getConstantOfType(Object.class), type);
    if (value != null) {
      return DfTypes.constant(value, type);
    }
    return DfTypes.TOP;
  }
}
