// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.Processor;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides low-level search and find usages services for a project, like finding references
 * to an element, finding overriding / inheriting elements, finding to do items and so on.
 *
 * Use {@link PsiSearchHelper#getInstance(Project)} to get a search helper instance.
 */
public interface PsiSearchHelper {
  class SERVICE {
    private SERVICE() {
    }

    /**
     * @deprecated please use {@link PsiSearchHelper#getInstance(Project)}
     */
    @Deprecated
    public static PsiSearchHelper getInstance(@NotNull Project project) {
      return PsiSearchHelper.getInstance(project);
    }
  }

  @NotNull
  static PsiSearchHelper getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PsiSearchHelper.class);
  }

  /**
   * Searches the specified scope for comments containing the specified identifier.
   *
   * @param identifier  the identifier to search.
   * @param searchScope the scope in which occurrences are searched.
   * @return the array of found comments.
   */
  @NotNull
  PsiElement[] findCommentsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope);

  /**
   * Processes the specified scope and hands comments containing the specified identifier over to the processor.
   *
   * @param identifier  the identifier to search.
   * @param searchScope the scope in which occurrences are searched.
   * @return false if processor returned false, true otherwise
   */
  boolean processCommentsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope, @NotNull Processor<PsiElement> processor);

  /**
   * Returns the list of files which contain the specified word in "plain text"
   * context (for example, plain text files or attribute values in XML files).
   *
   * @param word the word to search.
   * @return the list of files containing the word.
   */
  @NotNull
  PsiFile[] findFilesWithPlainTextWords(@NotNull String word);

  /**
   * Passes all occurrences of the specified full-qualified class name in plain text context
   * to the specified processor.
   *
   * @param qName       the class name to search.
   * @param processor   the processor which accepts the references.
   * @param searchScope the scope in which occurrences are searched.
   */
  boolean processUsagesInNonJavaFiles(@NotNull String qName,
                                      @NotNull PsiNonJavaFileReferenceProcessor processor,
                                      @NotNull GlobalSearchScope searchScope);

  /**
   * Passes all occurrences of the specified fully qualified class name in plain text context in the
   * use scope of the specified element to the specified processor.
   *
   * @param originalElement the element whose use scope is used to restrict the search scope,
   *                        or null if the search scope is not restricted.
   * @param qName           the class name to search.
   * @param processor       the processor which accepts the references.
   * @param searchScope     the scope in which occurrences are searched.
   */
  boolean processUsagesInNonJavaFiles(@Nullable PsiElement originalElement,
                                      @NotNull String qName,
                                      @NotNull PsiNonJavaFileReferenceProcessor processor,
                                      @NotNull GlobalSearchScope searchScope);

  /**
   * Returns the scope in which references to the specified element are searched. This scope includes the result of
   * {@link com.intellij.psi.PsiElement#getUseScope()} and also the results returned from the registered
   * com.intellij.psi.search.UseScopeEnlarger instances.
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
  boolean processAllFilesWithWord(@NotNull String word,
                                  @NotNull GlobalSearchScope scope,
                                  @NotNull Processor<PsiFile> processor,
                                  final boolean caseSensitively);

  /**
   * Passes all files containing the specified word in {@link UsageSearchContext#IN_PLAIN_TEXT plain text}
   * context to the specified processor.
   *
   * @param word      the word to search.
   * @param scope     the scope in which occurrences are searched.
   * @param processor the processor which accepts the references.
   * @param caseSensitively if words differing in the case only should not be considered equal
   */
  boolean processAllFilesWithWordInText(@NotNull String word,
                                        @NotNull GlobalSearchScope scope,
                                        @NotNull Processor<PsiFile> processor,
                                        final boolean caseSensitively);

  /**
   * Passes all files containing the specified word in {@link UsageSearchContext#IN_COMMENTS comments}
   * context to the specified processor.
   *
   * @param word      the word to search.
   * @param scope     the scope in which occurrences are searched.
   * @param processor the processor which accepts the references.
   */
  boolean processAllFilesWithWordInComments(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor);

  /**
   * Passes all files containing the specified word in {@link UsageSearchContext#IN_STRINGS string literal}
   * context to the specified processor.
   *
   * @param word      the word to search.
   * @param scope     the scope in which occurrences are searched.
   * @param processor the processor which accepts the references.
   */
  boolean processAllFilesWithWordInLiterals(@NotNull String word, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiFile> processor);

  boolean processRequests(@NotNull SearchRequestCollector request, @NotNull Processor<? super PsiReference> processor);

  @NotNull
  AsyncFuture<Boolean> processRequestsAsync(@NotNull SearchRequestCollector request, @NotNull Processor<? super PsiReference> processor);

  boolean processElementsWithWord(@NotNull TextOccurenceProcessor processor,
                                  @NotNull SearchScope searchScope,
                                  @NotNull String text,
                                  @MagicConstant(flagsFromClass = UsageSearchContext.class) short searchContext,
                                  boolean caseSensitive);

  boolean processElementsWithWord(@NotNull TextOccurenceProcessor processor,
                                  @NotNull SearchScope searchScope,
                                  @NotNull String text,
                                  @MagicConstant(flagsFromClass = UsageSearchContext.class) short searchContext,
                                  boolean caseSensitive,
                                  boolean processInjectedPsi);

  @NotNull
  AsyncFuture<Boolean> processElementsWithWordAsync(
                                       @NotNull TextOccurenceProcessor processor,
                                       @NotNull SearchScope searchScope,
                                       @NotNull String text,
                                       short searchContext,
                                       boolean caseSensitive);


  @NotNull
  SearchCostResult isCheapEnoughToSearch(@NotNull String name,
                                         @NotNull GlobalSearchScope scope,
                                         @Nullable PsiFile fileToIgnoreOccurrencesIn,
                                         @Nullable ProgressIndicator progress);

  enum SearchCostResult {
    ZERO_OCCURRENCES, FEW_OCCURRENCES, TOO_MANY_OCCURRENCES
  }
}
