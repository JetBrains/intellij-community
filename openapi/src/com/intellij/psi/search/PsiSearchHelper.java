
/*
* Copyright 2000-2005 JetBrains s.r.o.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.intellij.psi.search;

import com.intellij.aspects.psi.PsiPointcut;
import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.psi.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

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

  PsiIdentifier[] findIdentifiers(String identifier, SearchScope searchScope, short searchContext);
  boolean processIdentifiers(PsiElementProcessor<PsiIdentifier> processor, String identifier, SearchScope searchScope, short searchContext);

  PsiElement[] findCommentsContainingIdentifier(String identifier, SearchScope searchScope);
  PsiLiteralExpression[] findStringLiteralsContainingIdentifier(String identifier, SearchScope searchScope);

  PsiElement[] findJoinPointsByPointcut(PsiPointcut pointcut, SearchScope searchScope);
  boolean processJoinPointsByPointcut(PsiElementProcessor processor, PsiPointcut pointcut, SearchScope searchScope);

  boolean processAllClasses(PsiElementProcessor<PsiClass> processor, SearchScope searchScope);
  PsiClass[] findAllClasses(SearchScope searchScope);

  PsiFile[] findFilesWithTodoItems();
  TodoItem[] findTodoItems(PsiFile file);
  TodoItem[] findTodoItems(PsiFile file, int startOffset, int endOffset);
  int getTodoItemsCount(PsiFile file);
  int getTodoItemsCount(PsiFile file, TodoPattern pattern);

  PsiFile[] findFilesWithPlainTextWords(String word);
  void processUsagesInNonJavaFiles(String qName, PsiNonJavaFileReferenceProcessor processor, GlobalSearchScope searchScope);
  void processUsagesInNonJavaFiles(PsiElement originalElement, String qName, PsiNonJavaFileReferenceProcessor processor, GlobalSearchScope searchScope);

  @NotNull SearchScope getUseScope(PsiElement element);

  PsiFile[] findFormsBoundToClass(String className);

  boolean processReferencesIncludingOverriding(PsiReferenceProcessor processor,
                                               PsiMethod method,
                                               SearchScope searchScope,
                                               boolean isStrictSignatureSearch);


  boolean isFieldBoundToForm(PsiField field);

  void processAllFilesWithWord(String word, GlobalSearchScope scope, Processor<PsiFile> processor);
  void processAllFilesWithWordInComments(String word, GlobalSearchScope scope, Processor<PsiFile> processor);
  void processAllFilesWithWordInLiterals(String word, GlobalSearchScope scope, Processor<PsiFile> processor);
}
