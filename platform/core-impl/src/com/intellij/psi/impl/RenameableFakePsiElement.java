// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class RenameableFakePsiElement extends FakePsiElement implements PsiMetaOwner, PsiPresentableMetaData {
  private final PsiElement myParent;

  protected RenameableFakePsiElement(@Nullable PsiElement parent) {
    myParent = parent;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  private @NotNull PsiElement getParentNotNull(@NotNull String methodName) {
    if (myParent != null) {
      return myParent;
    }
    throw PluginException.createByClass(
      new AbstractMethodError("Elements initialized with `null` parent are expected to override `#" + methodName + "`"),
      getClass()
    );
  }

  @Override
  public PsiFile getContainingFile() {
    return getParentNotNull("getContainingFile").getContainingFile();
  }

  @Override
  public abstract String getName();

  @Override
  @NotNull
  public Language getLanguage() {
    return getContainingFile().getLanguage();
  }

  @Override
  @NotNull
  public Project getProject() {
    return getParentNotNull("getProject").getProject();
  }

  @Override
  public PsiManager getManager() {
    return PsiManager.getInstance(getProject());
  }

  @Override
  @Nullable
  public PsiMetaData getMetaData() {
    return this;
  }

  @Override
  public PsiElement getDeclaration() {
    return this;
  }

  @Override
  @NonNls
  public String getName(final PsiElement context) {
    return getName();
  }

  @Override
  public void init(final PsiElement element) {
  }

  @Override
  @Nullable
  public final Icon getIcon(final boolean open) {
    return getIcon();
  }

  @Override
  @Nullable
  public TextRange getTextRange() {
    return TextRange.from(0, 0);
  }
}
