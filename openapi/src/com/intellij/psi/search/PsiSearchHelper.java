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

import com.intellij.psi.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides low-level search and find usages services for a project, like finding references
 * to an element, finding overriding / inheriting elements, finding to do items and so on.
 *
 * @see com.intellij.psi.PsiManager#getSearchHelper()
 */
public interface PsiSearchHelper {
  /**
   * @deprecated Use <code>ReferencesSearch.search(...).findAll()</code> instead
   * Searches the specified scope for references to the specified element.
   *
   * @param element           the element to find the references to.
   * @param searchScope       the scope in which references are searched.
   * @param ignoreAccessScope if true, references inaccessible because of visibility rules are included in the list.
   * @return the array of found references.
   */
  @NotNull PsiReference[] findReferences(@NotNull PsiElement element, @NotNull SearchScope searchScope, boolean ignoreAccessScope);

  /**
   * @deprecated Use <code>ReferencesSearch.search(...).forEach(...)</code> instead
   * Passes all references to the specified element in the specified scope to the specified
   * processor.
   *
   * @param processor         processor which accepts found references.
   * @param element           the element references to which are searched.
   * @param searchScope       the scope in which references are searched.
   * @param ignoreAccessScope if true, references inaccessible because of visibility rules are included in the list.
   * @return true if the search was completed normally, if the reference processing was cancelled by the processor.
   */
  boolean processReferences(@NotNull PsiReferenceProcessor processor, @NotNull PsiElement element, @NotNull SearchScope searchScope, boolean ignoreAccessScope);

  /**
   * Searches the specified scope for classes or interfaces inheriting from the specified class
   * or interface.
   *
   * @param aClass      the class or interface for which the inheritors should be searched.
   * @param searchScope the scope in which inheritors are searched.
   * @param checkDeep   if true, the inheritor search is performed recursively (inheritors of
   *                    inheritors and so on are included in the results).
   * @return the array of inheritor classes.
   */
  @NotNull
  PsiClass[] findInheritors(@NotNull PsiClass aClass, @NotNull SearchScope searchScope, boolean checkDeep);

  /**
   * Passes all classes/interfaces inheriting from the specified class/interface in the specified
   * scope to the specified processor.
   *
   * @param processor   processor which accepts found inheritors.
   * @param aClass      the class or interface for which the inheritors should be searched.
   * @param searchScope the scope in which inheritors are searched.
   * @param checkDeep   if true, the inheritor search is performed recursively (inheritors of
   *                    inheritors and so on are included in the results).
   * @return true if the search was completed normally, if the element processing was cancelled by the processor.
   */
  boolean processInheritors(@NotNull PsiElementProcessor<PsiClass> processor, @NotNull PsiClass aClass, @NotNull SearchScope searchScope, boolean checkDeep);

  /**
   * Passes all classes/interfaces inheriting from the specified class/interface in the specified
   * scope to the specified processor.
   *
   * @param processor        processor which accepts found inheritors.
   * @param aClass           the class or interface for which the inheritors should be searched.
   * @param searchScope      the scope in which inheritors are searched.
   * @param checkDeep        if true, the inheritor search is performed recursively (inheritors of
   *                         inheritors and so on are included in the results).
   * @param checkInheritance for optimization purposes: if false, the processor may additionally receive
   *                         classes inherited from a class with the same short name but different
   *                         full-qualified name
   * @return true if the search was completed normally, if the reference processing was cancelled by the processor.
   */
  boolean processInheritors(@NotNull PsiElementProcessor<PsiClass> processor,
                            @NotNull PsiClass aClass,
                            @NotNull SearchScope searchScope,
                            boolean checkDeep,
                            boolean checkInheritance);

  /**
   * Searches the specified scope for methods overriding the specified method.
   *
   * @param method      the method for which overriding methods should be searched.
   * @param searchScope the scope in which overriding methods are searched.
   * @param checkDeep   if true, the override search is performed recursively (overrides of
   *                    overrides and so on are included in the results).
   * @return the array of overriding methods.
   */
  @NotNull PsiMethod[] findOverridingMethods(@NotNull PsiMethod method, @NotNull SearchScope searchScope, boolean checkDeep);

  /**
   * Passes all methods overriding the specified method in the specified scope to the specified processor.
   *
   * @param processor   processor which accepts found overriding methods.
   * @param method      the method for which overriding methods should be searched.
   * @param searchScope the scope in which overriding methods are searched.
   * @param checkDeep   if true, the override search is performed recursively (overrides of
   *                    overrides and so on are included in the results).
   * @return true if the search was completed normally, if the element processing was cancelled by the processor.
   */
  boolean processOverridingMethods(@NotNull PsiElementProcessor<PsiMethod> processor, @NotNull PsiMethod method, @NotNull SearchScope searchScope, boolean checkDeep);


  /**
   * Searches the specified scope for references to the specified method and its overriding methods.
   *
   * @param method                  the method to find the references to.
   * @param searchScope             the scope in which references are searched.
   * @param isStrictSignatureSearch if false, references to methods overloading <code>method</code>
   *                                will also be included in the results.
   * @return the array of found references.
   */
  @NotNull PsiReference[] findReferencesIncludingOverriding(@NotNull PsiMethod method, @NotNull SearchScope searchScope, boolean isStrictSignatureSearch);

  /**
   * Passes all references to the specified method and its overriding methods in
   * the specified scope to the specified processor.
   *
   * @param processor   processor which accepts found references.
   * @param method      the method to find the references to.
   * @param searchScope the scope in which references are searched.
   * @return true if the search was completed normally, if the reference processing was cancelled by the processor.
   */
  boolean processReferencesIncludingOverriding(@NotNull PsiReferenceProcessor processor, @NotNull PsiMethod method, @NotNull SearchScope searchScope);

  /**
   * Passes all references to the specified method and its overriding methods in
   * the specified scope to the specified processor.
   *
   * @param processor               processor which accepts found references.
   * @param method                  the method to find the references to.
   * @param searchScope             the scope in which references are searched.
   * @param isStrictSignatureSearch if false, references to methods overloading <code>method</code>
   *                                will also be included in the results.
   * @return true if the search was completed normally, if the reference processing was cancelled by the processor.
   */
  boolean processReferencesIncludingOverriding(@NotNull PsiReferenceProcessor processor,
                                               @NotNull PsiMethod method,
                                               @NotNull SearchScope searchScope,
                                               boolean isStrictSignatureSearch);

  /**
   * Searches the specified scope for occurrences of the specified identifier.
   *
   * @param identifier    the identifier to search.
   * @param searchScope   the scope in which occurrences are searched.
   * @param searchContext the contexts in which identifiers are searched (a combination of flags
   *                      defined in the {@link UsageSearchContext} class).
   * @return the array of found identifiers.
   */
  @NotNull PsiIdentifier[] findIdentifiers(@NotNull String identifier, @NotNull SearchScope searchScope, short searchContext);

  /**
   * Passes the occurrences of the specified identifier in the specified scope to the specified
   * processor.
   *
   * @param processor     the processor which accepts found occurrences.
   * @param identifier    the identifier to search.
   * @param searchScope   the scope in which occurrences are searched.
   * @param searchContext the contexts in which identifiers are searched (a combination of flags
   *                      defined in the {@link UsageSearchContext} class).
   * @return true if the search was completed normally, if the occurrence processing was cancelled by the processor.
   */
  boolean processIdentifiers(@NotNull PsiElementProcessor<PsiIdentifier> processor, @NotNull String identifier, @NotNull SearchScope searchScope, short searchContext);

  /**
   * Searches the specified scope for comments containing the specified identifier.
   *
   * @param identifier  the identifier to search.
   * @param searchScope the scope in which occurrences are searched.
   * @return the array of found comments.
   */
  @NotNull PsiElement[] findCommentsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope);

  /**
   * Searches the specified scope for string literals containing the specified identifier.
   *
   * @param identifier  the identifier to search.
   * @param searchScope the scope in which occurrences are searched.
   * @return the array of found string literals.
   */
  @NotNull PsiLiteralExpression[] findStringLiteralsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope);

  /**
   * Passes all classes in the specified scope to the specified processor.
   *
   * @param processor   the processor which accepts found classes.
   * @param searchScope the scope in which classes are searched.
   * @return true if the search was completed normally, if the processing was cancelled by the processor.
   */
  boolean processAllClasses(@NotNull PsiElementProcessor<PsiClass> processor, @NotNull SearchScope searchScope);

  /**
   * Finds all classes in the specified scope.
   *
   * @param searchScope the scope to search for classes.
   * @return the array of found classes.
   */
  @NotNull PsiClass[] findAllClasses(@NotNull SearchScope searchScope);

  /**
   * Returns the list of all files in the project which have to do items.
   *
   * @return the list of files with to do items.
   */
  @NotNull PsiFile[] findFilesWithTodoItems();

  /**
   * Searches the specified file for to do items.
   *
   * @param file the file to search for to do items.
   * @return the array of found items.
   */
  @NotNull TodoItem[] findTodoItems(@NotNull PsiFile file);

  /**
   * Searches the specified range of text in the specified file for to do items.
   *
   * @param file        the file to search for to do items.
   * @param startOffset the start offset of the text range to search to do items in.
   * @param endOffset   the end offset of the text range to search to do items in.
   * @return the array of found items.
   */
  @NotNull TodoItem[] findTodoItems(@NotNull PsiFile file, int startOffset, int endOffset);

  /**
   * Returns the number of to do items in the specified file.
   *
   * @param file the file to return the to do count for.
   * @return the count of to do items in the file.
   */
  int getTodoItemsCount(@NotNull PsiFile file);

  /**
   * Returns the number of to do items matching the specified pattern in the specified file.
   *
   * @param file    the file to return the to do count for.
   * @param pattern the pattern of to do items to find.
   * @return the count of to do items in the file.
   */
  int getTodoItemsCount(@NotNull PsiFile file, @NotNull TodoPattern pattern);

  /**
   * Returns the list of files which contain the specified word in "plain text"
   * context (for example, plain text files or attribute values in XML files).
   *
   * @param word the word to search.
   * @return the list of files containing the word.
   */
  @NotNull PsiFile[] findFilesWithPlainTextWords(@NotNull String word);

  /**
   * Passes all occurrences of the specified full-qualified class name in plain text context
   * to the specified processor.
   *
   * @param qName       the class name to search.
   * @param processor   the processor which accepts the references.
   * @param searchScope the scope in which occurrences are searched.
   */
  void processUsagesInNonJavaFiles(@NotNull String qName, @NotNull PsiNonJavaFileReferenceProcessor processor, @NotNull GlobalSearchScope searchScope);

  /**
   * Passes all occurrences of the specified full-qualified class name in plain text context in the
   * use scope of the specified element to the specified processor.
   *
   * @param originalElement the element whose use scope is used to restrict the search scope,
   *                        or null if the search scope is not restricted.
   * @param qName           the class name to search.
   * @param processor       the processor which accepts the references.
   * @param searchScope     the scope in which occurrences are searched.
   */
  void processUsagesInNonJavaFiles(@Nullable PsiElement originalElement,
                                   @NotNull String qName,
                                   @NotNull PsiNonJavaFileReferenceProcessor processor,
                                   @NotNull GlobalSearchScope searchScope);

  /**
   * Returns the scope in which references to the specified element are searched.
   *
   * @param element the element to return the use scope form.
   * @return the search scope instance.
   */
  @NotNull
  SearchScope getUseScope(@NotNull PsiElement element);

  /**
   * Finds GUI Designer forms bound to the specified class.
   *
   * @param className the fully-qualified name of the class to find bound forms for.
   * @return the array of found bound forms.
   */
  @NotNull PsiFile[] findFormsBoundToClass(String className);

  /**
   * Checks if the specified field is bound to a GUI Designer form component.
   *
   * @param field the field to check the binding for.
   * @return true if the field is bound, false otherwise.
   */
  boolean isFieldBoundToForm(@NotNull PsiField field);

  /**
   * Passes all files containing the specified word in {@link UsageSearchContext#IN_CODE code}
   * context to the specified processor.
   *
   * @param word      the word to search.
   * @param scope     the scope in which occurrences are searched.
   * @param processor the processor which accepts the references.
   * @param caseSensitively if words differing in the case only should not be considered equal
   */
  void processAllFilesWithWord(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor, final boolean caseSensitively);

  /**
   * Passes all files containing the specified word in {@link UsageSearchContext#IN_PLAIN_TEXT code}
   * context to the specified processor.
   *
   * @param word      the word to search.
   * @param scope     the scope in which occurrences are searched.
   * @param processor the processor which accepts the references.
   * @param caseSensitively if words differing in the case only should not be considered equal
   */
  void processAllFilesWithWordInText(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor, final boolean caseSensitively);

  /**
   * Passes all files containing the specified word in {@link UsageSearchContext#IN_COMMENTS comments}
   * context to the specified processor.
   *
   * @param word      the word to search.
   * @param scope     the scope in which occurrences are searched.
   * @param processor the processor which accepts the references.
   */
  void processAllFilesWithWordInComments(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor);

  /**
   * Passes all files containing the specified word in {@link UsageSearchContext#IN_STRINGS string literal}
   * context to the specified processor.
   *
   * @param word      the word to search.
   * @param scope     the scope in which occurrences are searched.
   * @param processor the processor which accepts the references.
   */
  void processAllFilesWithWordInLiterals(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor);

  boolean processElementsWithWord(@NotNull TextOccurenceProcessor processor,
                                  @NotNull SearchScope searchScope,
                                  @NotNull String text,
                                  short searchContext,
                                  boolean caseSensitive);
}
