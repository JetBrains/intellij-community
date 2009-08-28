/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
*/
public abstract class RenameableFakePsiElement extends FakePsiElement implements PsiMetaOwner, PsiPresentableMetaData {
  private final PsiElement myParent;

  protected RenameableFakePsiElement(final PsiElement parent) {
    myParent = parent;
  }

  public PsiFile getContainingFile() {
    return myParent.getContainingFile();
  }

  public abstract String getName();

  @NotNull
  public Language getLanguage() {
    return getContainingFile().getLanguage();
  }

  @NotNull
  public Project getProject() {
    return myParent.getProject();
  }

  public PsiManager getManager() {
    return PsiManager.getInstance(getProject());
  }

  @Nullable
  public PsiMetaData getMetaData() {
    return this;
  }

  public PsiElement getDeclaration() {
    return this;
  }

  @NonNls
  public String getName(final PsiElement context) {
    return getName();
  }

  public void init(final PsiElement element) {
  }

  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Nullable
  public final Icon getIcon(final boolean open) {
    return getIcon();
  }

  @Nullable
  public TextRange getTextRange() {
    return TextRange.from(0, 0);
  }
}
