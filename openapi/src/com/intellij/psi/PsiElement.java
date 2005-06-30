/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiElement extends UserDataHolder, Iconable {
  PsiElement[] EMPTY_ARRAY = new PsiElement[0];

  Project getProject();

  @NotNull Language getLanguage();

  PsiManager getManager();

  @NotNull PsiElement[] getChildren();

  PsiElement getParent();

  @Nullable  PsiElement getFirstChild();

  @Nullable  PsiElement getLastChild();

  @Nullable  PsiElement getNextSibling();

  @Nullable  PsiElement getPrevSibling();

  PsiFile getContainingFile();

  TextRange getTextRange();

  int getStartOffsetInParent();

  int getTextLength();

  PsiElement findElementAt(int offset);

  PsiReference findReferenceAt(int offset);

  int getTextOffset();

  String getText();

  @Nullable char[] textToCharArray();

  PsiElement getNavigationElement();
  PsiElement getOriginalElement();

  //Q: get rid of these methods?
  boolean textMatches(@NotNull CharSequence text);

  boolean textMatches(@NotNull PsiElement element);

  boolean textContains(char c);

  void accept(@NotNull PsiElementVisitor visitor);

  void acceptChildren(@NotNull PsiElementVisitor visitor);

  PsiElement copy();

  PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException;

  PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException;

  PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException;

  void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException;

  PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException;

  PsiElement addRangeBefore(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException;

  PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException;

  void delete() throws IncorrectOperationException;

  void checkDelete() throws IncorrectOperationException;

  void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException;

  PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException;

  boolean isValid();

  boolean isWritable();

  PsiReference getReference();

  @NotNull PsiReference[] getReferences();

  <T> T getCopyableUserData(Key<T> key);

  <T> void putCopyableUserData(Key<T> key, T value);

  boolean processDeclarations(PsiScopeProcessor processor,
                              PsiSubstitutor substitutor,
                              PsiElement lastParent,
                              PsiElement place);

  /** For resolve purposes (correct getParent in case of setContext binding) */
  PsiElement getContext();

  boolean isPhysical();

  GlobalSearchScope getResolveScope();
  SearchScope getUseScope();

  ASTNode getNode();
}
