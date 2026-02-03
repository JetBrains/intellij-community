/*
 * Copyright (C) 2002-2024 Sebastiano Vigna
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fleet.fastutil

/** Basic data for all hash-based classes.  */
interface Hash {
  /** A generic hash strategy.
   *
   *
   * Custom hash structures (e.g., [ ]) allow to hash objects
   * using arbitrary functions, a typical example being that of [ ][fleet.fastutil.ints.IntArrays.HASH_STRATEGY]. Of course,
   * one has to compare objects for equality consistently with the chosen
   * function. A *hash strategy*, thus, specifies an [ ][.equals] and a [ ][.hashCode], with the obvious property that
   * equal objects must have the same hash code.
   *
   *
   * Note that the [equals()][.equals] method of a strategy must
   * be able to handle `null`, too.
   */
  interface Strategy<K> {
    /** Returns the hash code of the specified object with respect to this hash strategy.
     *
     * @param o an object (or `null`).
     * @return the hash code of the given object with respect to this hash strategy.
     */
    fun hashCode(o: K?): Int

    /** Returns true if the given objects are equal with respect to this hash strategy.
     *
     * @param a an object (or `null`).
     * @param b another object (or `null`).
     * @return true if the two specified objects are equal with respect to this hash strategy.
     */
    fun equals(a: K?, b: K?): Boolean
  }

  companion object {
    /** The initial default size of a hash table.  */
    const val DEFAULT_INITIAL_SIZE: Int = 16

    /** The default load factor of a hash table.  */
    const val DEFAULT_LOAD_FACTOR: Float = .75f
  }
}