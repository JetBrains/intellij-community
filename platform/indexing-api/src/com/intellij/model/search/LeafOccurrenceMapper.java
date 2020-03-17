// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.LocalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Normally, text occurrences are processed from the bottom to the top (file),
 * see {@link SearchWordQueryBuilder#buildOccurrenceQuery}. <br/>
 * Given an occurrence in {@code leaf3}, the walk up is started there,
 * and it goes all the way to the top: {@code leaf3}, {@code node2}, ..., {@code file}.
 * <pre>
 * 0              file
 *               /  |  \
 * ...       ...   ...  ...
 *           /            \
 * N-1     node1         node2
 *        /     \       /     \
 * N   leaf1  leaf2  leaf3  leaf4
 * </pre>
 * Some search might need only immediate parent (the node at {@code N-1}th level)
 * so it could stop walking up there and return early.
 *
 * @param <T> type of the query result
 */
public interface LeafOccurrenceMapper<T> {

  /**
   * This method is called once per offset in {@code scope},
   * so implementations are able to control whether to go up the tree or not.
   *
   * @param scope         the top-most element with the occurrence;
   *                      in case the search is conducted in {@link LocalSearchScope},
   *                      this would be one of {@link LocalSearchScope#getScope scope elements},
   *                      in other cases the {@code scope} is a containing file of {@code start}
   * @param start         the bottom-most element containing whole occurrence, usually a leaf element
   * @param offsetInStart start offset of the occurrence in {@code start}
   * @return read-only collection of result elements of the query
   */
  @NotNull
  Collection<T> mapOccurrence(@NotNull PsiElement scope, @NotNull PsiElement start, int offsetInStart);
}
