// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.search

import com.intellij.model.Pointer
import org.jetbrains.annotations.ApiStatus.OverrideOnly
import org.jetbrains.annotations.Contract

/**
 * Normally, text occurrences are processed from the bottom to the top (file),
 * see [SearchWordQueryBuilder.buildOccurrenceQuery].
 *
 * Given an occurrence in `leaf3`, the walk-up is started there,
 * and it goes all the way to the top: `leaf3`, `node2`, ..., `file`.
 * ```
 * 0              file
 *               /  |  \
 * ...       ...   ...  ...
 *           /            \
 * N-1     node1         node2
 *        /     \       /     \
 * N   leaf1  leaf2  leaf3  leaf4
 * ```
 *
 * Some search might need only immediate parent (the node at `N-1`th level)
 * so it could stop walking up there and return early.
 *
 * @param T type of the query result
 */
@OverrideOnly
fun interface LeafOccurrenceMapper<T : Any> {

  /**
   * This method is called once per offset in `scope`,
   * so implementations are able to control whether to go up the tree or not.
   *
   * @return read-only collection of result elements of the query
   */
  fun mapOccurrence(occurrence: LeafOccurrence): Collection<@JvmWildcard T>

  fun interface Parameterized<P : Any, T : Any> {

    fun mapOccurrence(parameter: P, occurrence: LeafOccurrence): Collection<@JvmWildcard T>
  }

  companion object {

    /**
     * @param P type of pointer value
     * @param T type of mapper result
     * @return mapper, which dereferences the [pointer], and,
     * if successful, passes the value to the [parameterizedMapper] along with the occurrence
     */
    @JvmStatic
    @Contract(pure = true)
    fun <P : Any, T : Any> withPointer(
      pointer: Pointer<out P>,
      parameterizedMapper: Parameterized<in P, out T>,
    ): LeafOccurrenceMapper<out T> = LeafOccurrenceMapper { occurrence: LeafOccurrence ->
      val value = pointer.dereference()
      if (value == null) {
        emptyList()
      }
      else {
        parameterizedMapper.mapOccurrence(value, occurrence)
      }
    }
  }
}
