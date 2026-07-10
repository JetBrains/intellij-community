// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.keyFMap.KeyFMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

class HighlightFakePsiElement extends FakePsiElement {
  private final @NotNull String myDebugString;

  private HighlightFakePsiElement(@NotNull String debugString) { myDebugString = debugString; }

  @Override
  public @NotNull Project getProject() {
    throw createException();
  }

  @Override
  public @NotNull Language getLanguage() {
    throw createException();
  }

  @Override
  public PsiManager getManager() {
    throw createException();
  }

  @Override
  public PsiElement getParent() {
    return null;
  }

  @Override
  public PsiFile getContainingFile() {
    return null;
  }

  @Override
  public TextRange getTextRange() {
    return TextRange.EMPTY_RANGE;
  }

  @Override
  public int getStartOffsetInParent() {
    return -1;
  }

  @Override
  public PsiElement findElementAt(int offset) {
    return null;
  }

  @Override
  public @Nullable PsiReference findReferenceAt(int offset) {
    return null;
  }

  @Override
  public String getText() {
    return "";
  }

  @Override
  public char @NotNull [] textToCharArray() {
    return ArrayUtil.EMPTY_CHAR_ARRAY;
  }

  @Override
  public PsiElement getOriginalElement() {
    return null;
  }

  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    return false;
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
  }

  @Override
  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
  }

  @Override
  public PsiElement copy() {
    return null;
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) {
    throw createException();
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) {
    throw createException();
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) {
    throw createException();
  }

  @Override
  public void checkAdd(@NotNull PsiElement element) {
    throw createException();
  }

  @Override
  public PsiElement addRange(PsiElement first, PsiElement last) {
    throw createException();
  }

  @Override
  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor) {
    throw createException();
  }

  @Override
  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) {
    throw createException();
  }

  @Override
  public void delete() {
    throw createException();
  }

  @Override
  public void checkDelete() {
    throw createException();
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) {
    throw createException();
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) {
    throw createException();
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  PsiInvalidElementAccessException createException() {
    return new PsiInvalidElementAccessException(this, toString(), null);
  }

  @Override
  public @Nullable PsiReference getReference() {
    return null;
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return PsiReference.EMPTY_ARRAY;
  }

  @Override
  public <T> T getCopyableUserData(@NotNull Key<T> key) {
    throw createException();
  }

  @Override
  public <T> void putCopyableUserData(@NotNull Key<T> key, T value) {
    throw createException();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return false;
  }

  @Override
  public PsiElement getContext() {
    return null;
  }

  @Override
  public boolean isPhysical() {
    return true;
  }

  @Override
  public @NotNull GlobalSearchScope getResolveScope() {
    throw createException();
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    throw createException();
  }

  @Override
  public ASTNode getNode() {
    throw createException();
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    throw createException();
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, T value) {
    throw createException();
  }

  @Override
  public Icon getIcon(int flags) {
    throw createException();
  }

  @Override
  public String toString() {
    return "FAKE_PSI_ELEMENT: " + myDebugString;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw createException();
  }

  @Override
  protected void setUserMap(@NotNull KeyFMap map) {
    throw createException();
  }

  static @NotNull PsiElement create(@NotNull String debugString) {
    return new HighlightFakePsiElement(debugString);
  }
}
