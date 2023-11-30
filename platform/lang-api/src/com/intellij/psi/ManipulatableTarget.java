// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomRenameableTarget;
import com.intellij.pom.PsiDeclaredTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManipulatableTarget extends DelegatePsiTarget implements PsiDeclaredTarget, PomRenameableTarget<Object> {
  public ManipulatableTarget(@NotNull PsiElement element) {
    super(element);
  }

  @Override
  public TextRange getNameIdentifierRange() {
    return ElementManipulators.getValueTextRange(getNavigationElement());
  }

  @Override
  public boolean isWritable() {
    return getNavigationElement().isWritable();
  }

  @Override
  public @Nullable Object setName(@NotNull String newName) {
    ElementManipulators.handleContentChange(getNavigationElement(), newName);
    return null;
  }

  @Override
  public String getName() {
    return ElementManipulators.getValueText(getNavigationElement());
  }
}
