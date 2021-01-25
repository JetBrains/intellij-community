// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Pointer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

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
   * @return read-only collection of result elements of the query
   */
  @NotNull Collection<? extends T> mapOccurrence(@NotNull LeafOccurrence occurrence);

  interface Parameterized<P, T> {

    @NotNull Collection<? extends T> mapOccurrence(P parameter, @NotNull LeafOccurrence occurrence);
  }

  /**
   * @param <P> type of pointer value
   * @param <T> type of mapper result
   * @return mapper, which dereferences the {@code pointer}, and,
   * if successful, passes the value to the {@code parameterizedMapper} along with the occurrence
   */
  @Contract(pure = true)
  static <P, T> @NotNull LeafOccurrenceMapper<T> withPointer(
    @NotNull Pointer<? extends P> pointer,
    @NotNull Parameterized<@NotNull ? super P, ? extends T> parameterizedMapper
  ) {
    return occurrence -> {
      P value = pointer.dereference();
      return value == null ? Collections.emptyList()
                           : parameterizedMapper.mapOccurrence(value, occurrence);
    };
  }
}
