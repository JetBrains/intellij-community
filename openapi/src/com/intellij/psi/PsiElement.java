/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;

public interface PsiElement extends UserDataHolder, Iconable {
  PsiElement[] EMPTY_ARRAY = new PsiElement[0];

  Project getProject();

  PsiManager getManager();

  PsiElement[] getChildren();

  PsiElement getParent();

  PsiElement getFirstChild();

  PsiElement getLastChild();

  PsiElement getNextSibling();

  PsiElement getPrevSibling();

  PsiFile getContainingFile();

  TextRange getTextRange();

  int getStartOffsetInParent();

  int getTextLength();

  PsiElement findElementAt(int offset);

  PsiReference findReferenceAt(int offset);

  int getTextOffset();

  String getText();

  char[] textToCharArray();

  PsiElement getNavigationElement();
  PsiElement getOriginalElement();

  //Q: get rid of these methods?
  boolean textMatches(CharSequence text);

  boolean textMatches(PsiElement element);

  boolean textContains(char c);

  void accept(PsiElementVisitor visitor);

  void acceptChildren(PsiElementVisitor visitor);

  PsiElement copy();

  PsiElement add(PsiElement element) throws IncorrectOperationException;

  PsiElement addBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException;

  PsiElement addAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException;

  void checkAdd(PsiElement element) throws IncorrectOperationException;

  void checkAddBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException;

  void checkAddAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException;

  PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException;

  PsiElement addRangeBefore(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException;

  PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException;

  void checkAddRange(PsiElement first, PsiElement last) throws IncorrectOperationException;

  void checkAddRangeBefore(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException;

  void checkAddRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException;

  void delete() throws IncorrectOperationException;

  void checkDelete() throws IncorrectOperationException;

  void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException;

  PsiElement replace(PsiElement newElement) throws IncorrectOperationException;

  void checkReplace(PsiElement newElement) throws IncorrectOperationException;

  boolean isValid();

  boolean isWritable();

  PsiReference getReference();

  PsiReference[] getReferences();

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
  GlobalSearchScope getUseScope();
}
