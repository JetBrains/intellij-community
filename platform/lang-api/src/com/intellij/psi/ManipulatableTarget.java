// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.pom.PomRenameableTarget;
import com.intellij.openapi.util.TextRange;
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
  @Nullable
  public Object setName(@NotNull String newName) {
    ElementManipulators.handleContentChange(getNavigationElement(), newName);
    return null;
  }

  @Override
  public String getName() {
    return ElementManipulators.getValueText(getNavigationElement());
  }
}
