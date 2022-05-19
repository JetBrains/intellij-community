// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * <a href="https://openjdk.java.net/jeps/405">JEP</a>
 */
public interface PsiRecordPattern extends PsiPrimaryPattern, PsiNamedElement {
  @NotNull
  PsiRecordStructurePattern getStructurePattern();

  @NotNull
  PsiTypeElement getTypeElement();
}
