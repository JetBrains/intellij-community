// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
  public @NotNull Language getLanguage() {
    return Language.ANY;
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public @Nullable PsiElement getFirstChild() {
    return null;
  }

  @Override
  public @Nullable PsiElement getLastChild() {
    return null;
  }

  @Override
  public @Nullable PsiElement getNextSibling() {
    return null;
  }

  @Override
  public @Nullable PsiElement getPrevSibling() {
    return null;
  }

  @Override
  public @Nullable TextRange getTextRange() {
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
  public @Nullable PsiElement findElementAt(int offset) {
    return null;
  }

  @Override
  public int getTextOffset() {
    return 0;
  }

  @Override
  public @Nullable @NonNls String getText() {
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
  public @Nullable ASTNode getNode() {
    return null;
  }

  @Override
  public String getPresentableText() {
    return getName();
  }

  @Override
  public final Icon getIcon(int flags) {
    return super.getIcon(flags);
  }

  @Override
  protected final Icon getElementIcon(int flags) {
    return super.getElementIcon(flags);
  }

  @Override
  public @Nullable Icon getIcon(boolean open) {
    return null;
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return null;
  }

  @Override
  public PsiManager getManager() {
    PsiElement parent = getParent();
    return parent != null ? parent.getManager() : null;
  }

  @Override
  public boolean isPhysical() {
    return false;
  }
}
