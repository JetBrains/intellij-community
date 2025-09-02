// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * {@link PsiElementVisitor} that exposes desired element classes to visit. Called once per each run of inspection tool.
 * Inspection engine then may skip elements with all types not in this list.
 * <p>
 * Limitations:
 * <ul>
 * <li>It must always return the same set of classes</li>
 * <li>The result may not depend on any configuration/settings</li>
 * <li>Implementations must override {@link PsiElementVisitor#visitElement(PsiElement)}</li>
 * </ul>
 */
@ApiStatus.Internal
public interface HintedPsiElementVisitor {
  /**
   * @return PSI element classes to visit
   */
  @NotNull @Unmodifiable
  List<Class<?>> getHintPsiElements();
}
