// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.light;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class LightElement extends PsiElementBase {
  protected final PsiManager myManager;
  private final Language myLanguage;
  private volatile PsiElement myNavigationElement = this;

  protected LightElement(@NotNull PsiManager manager, @NotNull Language language) {
    myManager = manager;
    myLanguage = language;
  }

  @Override
  public @NotNull Language getLanguage() {
    return myLanguage;
  }

  @Override
  public PsiManager getManager() {
    return myManager;
  }

  @Override
  public PsiElement getParent() {
    return null;
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public PsiFile getContainingFile() {
    return null;
  }

  @Override
  public TextRange getTextRange() {
    return null;
  }

  @Override
  public int getStartOffsetInParent() {
    return -1;
  }

  @Override
  public final int getTextLength() {
    String text = getText();
    return text != null ? text.length() : 0;
  }

  @Override
  public char @NotNull [] textToCharArray() {
    return getText().toCharArray();
  }

  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    return getText().equals(text.toString());
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return getText().equals(element.getText());
  }

  @Override
  public PsiElement findElementAt(int offset) {
    return null;
  }

  @Override
  public int getTextOffset() {
    return -1;
  }

  @Override
  public boolean isValid() {
    PsiElement navElement = getNavigationElement();
    if (navElement != this) {
      return navElement.isValid();
    }

    return true;
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public boolean isPhysical() {
    return false;
  }

  @Override
  public abstract String toString();

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public void delete() throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public ASTNode getNode() {
    return null;
  }

  @Override
  public String getText() {
    return null;
  }

  @Override
  public PsiElement copy() {
    return null;
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    return myNavigationElement;
  }

  public void setNavigationElement(@NotNull PsiElement navigationElement) {
    PsiElement nnElement = navigationElement.getNavigationElement();
    if (nnElement != navigationElement && nnElement != null) {
      navigationElement = nnElement;
    }
    myNavigationElement = navigationElement;
  }

  @Override
  public PsiElement getPrevSibling() {
    return null;
  }

  @Override
  public PsiElement getNextSibling() {
    return null;
  }

}
