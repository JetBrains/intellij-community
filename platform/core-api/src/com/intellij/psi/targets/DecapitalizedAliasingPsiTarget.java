// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.targets;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;

public class DecapitalizedAliasingPsiTarget extends AliasingPsiTarget {

  public DecapitalizedAliasingPsiTarget(@NotNull PsiNamedElement element) {
    super(element);
  }

  @Override
  public @NotNull String getNameAlias(@NotNull String psiTargetName) {
    return StringUtil.decapitalize(psiTargetName);
  }

  @Override
  public @NotNull String getTargetName(@NotNull String aliasName) {
    return StringUtil.capitalize(aliasName);
  }
}
