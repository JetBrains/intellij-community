// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class IntLiteralMayBeLongLiteralInspection extends CastedLiteralMaybeJustLiteralInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  @NonNls
  String getSuffix() {
    return "L";
  }

  @NotNull
  @Override
  PsiType getTypeBeforeCast() {
    return PsiTypes.intType();
  }

  @NotNull
  @Override
  PsiPrimitiveType getCastType() {
    return PsiTypes.longType();
  }
}
