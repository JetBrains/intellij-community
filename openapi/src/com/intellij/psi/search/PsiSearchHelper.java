
 /*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search;

import com.intellij.aspects.psi.PsiPointcut;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspDirective;

public interface PsiSearchHelper {
  PsiReference[] findReferences(PsiElement element, SearchScope searchScope, boolean ignoreAccessScope);
  boolean processReferences(PsiReferenceProcessor processor, PsiElement element, SearchScope searchScope, boolean ignoreAccessScope);

  PsiClass[] findInheritors(PsiClass aClass, SearchScope searchScope, boolean checkDeep);
  boolean processInheritors(PsiElementProcessor<PsiClass> processor, PsiClass aClass, SearchScope searchScope, boolean checkDeep);
  boolean processInheritors(PsiElementProcessor<PsiClass> processor, PsiClass aClass, SearchScope searchScope, boolean checkDeep, boolean checkInheritance);

  PsiMethod[] findOverridingMethods(PsiMethod method, SearchScope searchScope, boolean checkDeep);
  boolean processOverridingMethods(PsiElementProcessor<PsiMethod> processor, PsiMethod method, SearchScope searchScope, boolean checkDeep);

  PsiPointcutDef[] findOverridingPointcuts(PsiPointcutDef pointcut, SearchScope searchScope, boolean checkDeep);
  boolean processOverridingPointcuts(PsiElementProcessor processor, PsiPointcutDef pointcut, SearchScope searchScope, boolean checkDeep);

  PsiReference[] findReferencesIncludingOverriding(PsiMethod method, SearchScope searchScope, boolean isStrictSignatureSearch);
  boolean processReferencesIncludingOverriding(PsiReferenceProcessor processor, PsiMethod method, SearchScope searchScope);

  PsiIdentifier[] findIdentifiers(String identifier, SearchScope searchScope, int position);
  boolean processIdentifiers(PsiElementProcessor processor, String identifier, SearchScope searchScope, int position);

  PsiElement[] findCommentsContainingIdentifier(String identifier, SearchScope searchScope);
  PsiLiteralExpression[] findStringLiteralsContainingIdentifier(String identifier, SearchScope searchScope);
                                                                                                
  PsiElement[] findJoinPointsByPointcut(PsiPointcut pointcut, SearchScope searchScope);
  boolean processJoinPointsByPointcut(PsiElementProcessor processor, PsiPointcut pointcut, SearchScope searchScope);

  boolean processAllClasses(PsiElementProcessor<PsiClass> processor, SearchScope searchScope);
  PsiClass[] findAllClasses(SearchScope searchScope);

  JspDirective[] findIncludeDirectives(PsiFile file, SearchScope searchScope);

  PsiFile[] findFilesWithTodoItems();
  TodoItem[] findTodoItems(PsiFile file);
  TodoItem[] findTodoItems(PsiFile file, int startOffset, int endOffset);
  int getTodoItemsCount(PsiFile file);
  int getTodoItemsCount(PsiFile file, TodoPattern pattern);

  PsiFile[] findFilesWithPlainTextWords(String word);
  void processUsagesInNonJavaFiles(String qName, PsiNonJavaFileReferenceProcessor processor, GlobalSearchScope searchScope);

  SearchScope getAccessScope(PsiElement element);

  PsiFile[] findFormsBoundToClass(String className);

  boolean processReferencesIncludingOverriding(PsiReferenceProcessor processor,
                                                       PsiMethod method,
                                                       SearchScope searchScope,
                                                       boolean isStrictSignatureSearch);


  abstract class FileSink {
    public abstract void foundFile(PsiFile file);
  }

  boolean isFieldBoundToForm(PsiField field);

  void processAllFilesWithWord(String word, GlobalSearchScope scope, FileSink sink);
  void processAllFilesWithWordInComments(String word, GlobalSearchScope scope, FileSink sink);
  void processAllFilesWithWordInLiterals(String word, GlobalSearchScope scope, FileSink sink);
}
