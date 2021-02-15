// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class FakePsiElement extends PsiElementBase implements PsiNamedElement, ItemPresentation {

  @Override
  public ItemPresentation getPresentation() {
    return this;
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return Language.ANY;
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  @Nullable
  public PsiElement getFirstChild() {
    return null;
  }

  @Override
  @Nullable
  public PsiElement getLastChild() {
    return null;
  }

  @Override
  @Nullable
  public PsiElement getNextSibling() {
    return null;
  }

  @Override
  @Nullable
  public PsiElement getPrevSibling() {
    return null;
  }

  @Override
  @Nullable
  public TextRange getTextRange() {
    return null;
  }

  @Override
  public int getStartOffsetInParent() {
    return 0;
  }

  @Override
  public int getTextLength() {
    return 0;
  }

  @Override
  @Nullable
  public PsiElement findElementAt(int offset) {
    return null;
  }

  @Override
  public int getTextOffset() {
    return 0;
  }

  @Override
  @Nullable
  @NonNls
  public String getText() {
    return null;
  }

  @Override
  public char @NotNull [] textToCharArray() {
    return new char[0];
  }

  @Override
  public boolean textContains(char c) {
    return false;
  }

  @Override
  @Nullable
  public ASTNode getNode() {
    return null;
  }

  @Override
  public String getPresentableText() {
    return getName();
  }

  @Override
  public final Icon getIcon(final int flags) {
    return super.getIcon(flags);
  }

  @Override
  protected final Icon getElementIcon(final int flags) {
    return super.getElementIcon(flags);
  }

  @Override
  @Nullable
  public Icon getIcon(boolean open) {
    return null;
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return null;
  }

  @Override
  public PsiManager getManager() {
    final PsiElement parent = getParent();
    return parent != null ? parent.getManager() : null;
  }

  @Override
  public boolean isPhysical() {
    return false;
  }
}
