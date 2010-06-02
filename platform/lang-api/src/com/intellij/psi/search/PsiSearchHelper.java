/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
   * Searches the specified scope for comments containing the specified identifier.
   *
   * @param identifier  the identifier to search.
   * @param searchScope the scope in which occurrences are searched.
   * @return the array of found comments.
   */
  @NotNull PsiElement[] findCommentsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope);

  /**
   * Processes the specified scope and hands comments containing the specified identifier over to the processor.
   *
   * @param identifier  the identifier to search.
   * @param searchScope the scope in which occurrences are searched.
   * @param processor
   * @return false if processor returned false, true otherwise
   */
  boolean processCommentsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope, @NotNull Processor<PsiElement> processor);

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

  boolean processRequest(@NotNull PsiSearchRequest request);

  boolean processElementsWithWord(@NotNull TextOccurenceProcessor processor,
                                  @NotNull SearchScope searchScope,
                                  @NotNull String text,
                                  short searchContext,
                                  boolean caseSensitive);

  SearchCostResult isCheapEnoughToSearch(@NotNull String name,
                                         @NotNull GlobalSearchScope scope,
                                         @Nullable PsiFile fileToIgnoreOccurencesIn,
                                         ProgressIndicator progress);

  enum SearchCostResult {
    ZERO_OCCURRENCES, FEW_OCCURRENCES, TOO_MANY_OCCURRENCES
  }
}
