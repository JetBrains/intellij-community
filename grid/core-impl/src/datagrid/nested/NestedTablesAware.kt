package com.intellij.database.datagrid.nested

import com.intellij.database.datagrid.NestedTable
import com.intellij.database.datagrid.NestedTablesDataGridModel.NestedTableCellCoordinate
import kotlin.math.max

/**
 * Represents a contract for a class that is aware of nested tables.
 *
 * @param T The type of the object returned by the methods.
 */
interface NestedTablesAware<T> {
  /**
   * This method is used to enter a nested table at the specified coordinate in a relative to the current table.
   *
   * @param coordinate The coordinate of the cell in the nested table where entering is required.
   * @param nestedTable The nested table to enter
   * @return The object of type T returned by the method.
   */
  suspend fun enterNestedTable(coordinate: NestedTableCellCoordinate, nestedTable: NestedTable): T

  /**
   * Exits the current nested table and returns to the parent table.
   *
   * @param steps The number of nested tables to exit. Must be a non-negative integer.
   * @return The object of type T returned by the method.
   */
  suspend fun exitNestedTable(steps: Int): T

  /**
   * Represents a non-empty stack data structure.
   *
   * @param E The type of elements stored in the stack.
   * @property storage The underlying storage for the stack, implemented as a MutableList.
   * @constructor Creates a NonEmptyStack with an initial top element.
   */
  class NonEmptyStack<E>(topElement: E, private val storage: MutableList<E> = mutableListOf(topElement)) : Iterable<E> {
    constructor(topElement: E) : this(topElement, mutableListOf(topElement))

    val size: Int
      get() = storage.size

    /**
     * Adds the given element to the top of the stack.
     *
     * @param element The element to be added to the stack.
     */
    fun push(element: E) {
      storage.add(element)
    }

    /**
     * Returns the last element in the storage.
     *
     * @return the last element in the storage
     */
    fun last(): E {
      return storage.last()
    }

    /**
     * Retrieves the first element from the storage.
     *
     * @return The first element from the storage.
     */
    fun first(): E {
      return storage.first()
    }

    /**
     * Returns a new list containing all elements in the storage except for the first element.
     *
     * @return a list containing all elements except the first element
     */
    fun allExceptFirst(): List<E> {
      return storage.subList(1, storage.size)
    }

    /**
     * Replaces the top element of the stack with the given element.
     *
     * @param element The element to replace the top element with.
     */
    fun replaceLast(element: E) {
      storage[storage.size - 1] = element
    }

    /**
     * Resets the NonEmptyStack by removing all elements except the first element and replacing the top element with the given element.
     *
     * @param element The element to replace the top element with.
     */
    fun reset(element: E) {
      storage.subList(1, storage.size).clear()
      replaceLast(element)
    }

    /**
     * Returns an iterator over the elements in this stack.
     *
     * @return an Iterator over the elements in this stack
     */
    override operator fun iterator(): Iterator<E> {
      return storage.iterator()
    }

    /**
     * Removes the specified number of elements from the top of the stack.
     *
     * @param num The number of elements to remove from the stack. Must be a non-negative integer.
     * @return The last element in the stack after removal.
     * @throws IllegalStateException if the specified number of elements is greater than or equal to the current stack depth.
     */
    fun pop(num: Int): E {
      if (num >= storage.size) {
        throw IllegalStateException("Current stack depth is ${storage.size}. Cannot remove $num elements.")
      }
      storage.subList(max(1, storage.size - num), storage.size).clear()

      return storage.last()
    }
  }
}
