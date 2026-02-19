// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.NotNull;

/**
 * @see com.intellij.pom.references.PomService#convertToPsi(PomTarget)
 */
@NonExtendable
public interface PomTargetPsiElement extends PsiElement {

  @NotNull PomTarget getTarget();

}
