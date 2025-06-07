// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.search;

import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@NonExtendable
public interface SearchRequest {

  /**
   * The <i>what</i> to search.
   * <p/>
   * The same target might be referenced differently in various languages.
   * Example 1.
   * JvmMethod with spaces in name is valid in JVM world, and might be provided by some JVM language support,
   * but it cannot be referenced in Java code, so Java implementation returns {@code null}, which means
   * <i>there are no such references in Java, don't even search there</i>.
   * <p/>
   * Example 2.
   * JvmMethod with {@code getFoo} name is referenced in Java by its exact name,
   * but in Groovy the same method might be referenced by both {@code getFoo} and {@code foo}.
   * In the end we have three queries: {@code getFoo} in Java, {@code getFoo} in Groovy, {@code foo} in Groovy.
   *
   * @return string to search for text occurrences,
   * which might contain references to the specified target
   */
  @Internal
  @NotNull String getSearchString();

  /**
   * The <i>where</i> to search.
   * <p/>
   * The method might not return search scope restricted by language,
   * since language restrictions are automatically applied by the platform.
   * <p/>
   * Example.
   * Consider a JvmField target representing a private Java field.<br/>
   * Java implementation knows that it can be referenced in Java code only from within its containing class.
   * Java implementation is able to obtain the PSI element representing the containing class (its implementation details),
   * which will serve as a <i>code usage scope</i> for the field in Java. <br/>
   * In Groovy a private field might be referenced by its name from anywhere,
   * so the <i>code usage scope</i> will effectively mean <i>in Groovy</i>.<br/>
   * Java Language Supports returns a search request with containing class as a search scope
   * and Groovy returns {@code null}. In the end we have two queries:
   * search field name in containing class in Java (if the class is in Java),
   * and search field name in Groovy language code.
   *
   * @return the scope where references to this target might occur
   * or {@code null} if there are no scope reductions to apply
   */
  @Internal
  default @Nullable SearchScope getSearchScope() {
    return null;
  }

  /**
   * The <i>where</i> to search for injections.
   *
   * @return the scope where references to this target might occur in the injected fragments
   * or {@code null} if there are no scope reductions to apply
   */
  @Internal
  default @Nullable SearchScope getInjectionSearchScope() {
    return getSearchScope();
  }

  static @NotNull SearchRequest of(@NotNull String searchString) {
    return () -> searchString;
  }

  static @NotNull SearchRequest of(@NotNull String searchString, @NotNull SearchScope searchScope) {
    return of(searchString, searchScope, searchScope);
  }

  static @NotNull SearchRequest of(@NotNull String searchString,
                                   @NotNull SearchScope searchScope,
                                   @NotNull SearchScope injectionSearchScope) {
    return new SearchRequest() {

      @Override
      public @NotNull String getSearchString() {
        return searchString;
      }

      @Override
      public @NotNull SearchScope getSearchScope() {
        return searchScope;
      }

      @Override
      public @NotNull SearchScope getInjectionSearchScope() {
        return injectionSearchScope;
      }
    };
  }
}
