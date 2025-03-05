// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.fastutil

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

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

/** Common code for all hash-based classes.  */
object HashCommon {
  /** 2<sup>32</sup>  ,  = (&#x221A;5  1)/2.  */
  private const val INT_PHI = -0x61c88647

  /** The reciprocal of [.INT_PHI] modulo 2<sup>32</sup>.  */
  private const val INV_INT_PHI = 0x144cbc89

  /** 2<sup>64</sup>  ,  = (&#x221A;5  1)/2.  */
  private const val LONG_PHI = -0x61c8864680b583ebL

  /** The reciprocal of [.LONG_PHI] modulo 2<sup>64</sup>.  */
  private const val INV_LONG_PHI = -0xe217c1e66c88cc3L

  /** Avalanches the bits of an integer by applying the finalisation step of MurmurHash3.
   *
   *
   * This method implements the finalisation step of Austin Appleby's [MurmurHash3](http://code.google.com/p/smhasher/).
   * Its purpose is to avalanche the bits of the argument to within 0.25% bias.
   *
   * @param x an integer.
   * @return a hash value with good avalanching properties.
   */
  fun murmurHash3(x: Int): Int {
    var x = x
    x = x xor (x ushr 16)
    x *= -0x7a143595
    x = x xor (x ushr 13)
    x *= -0x3d4d51cb
    x = x xor (x ushr 16)
    return x
  }


  /** Avalanches the bits of a long integer by applying the finalisation step of MurmurHash3.
   *
   *
   * This method implements the finalisation step of Austin Appleby's [MurmurHash3](http://code.google.com/p/smhasher/).
   * Its purpose is to avalanche the bits of the argument to within 0.25% bias.
   *
   * @param x a long integer.
   * @return a hash value with good avalanching properties.
   */
  fun murmurHash3(x: Long): Long {
    var x = x
    x = x xor (x ushr 33)
    x *= -0xae502812aa7333L
    x = x xor (x ushr 33)
    x *= -0x3b314601e57a13adL
    x = x xor (x ushr 33)
    return x
  }

  /** Quickly mixes the bits of an integer.
   *
   *
   * This method mixes the bits of the argument by multiplying by the golden ratio and
   * xorshifting the result. It is borrowed from [Koloboke](https://github.com/leventov/Koloboke), and
   * it has slightly worse behaviour than [.murmurHash3] (in open-addressing hash tables the average number of probes
   * is slightly larger), but it's much faster.
   *
   * @param x an integer.
   * @return a hash value obtained by mixing the bits of `x`.
   * @see .invMix
   */
  fun mix(x: Int): Int {
    val h: Int = x * INT_PHI
    return h xor (h ushr 16)
  }

  /** The inverse of [.mix]. This method is mainly useful to create unit tests.
   *
   * @param x an integer.
   * @return a value that passed through [.mix] would give `x`.
   */
  fun invMix(x: Int): Int {
    return (x xor (x ushr 16)) * INV_INT_PHI
  }

  /** Quickly mixes the bits of a long integer.
   *
   *
   * This method mixes the bits of the argument by multiplying by the golden ratio and
   * xorshifting twice the result. It is borrowed from [Koloboke](https://github.com/leventov/Koloboke), and
   * it has slightly worse behaviour than [.murmurHash3] (in open-addressing hash tables the average number of probes
   * is slightly larger), but it's much faster.
   *
   * @param x a long integer.
   * @return a hash value obtained by mixing the bits of `x`.
   */
  fun mix(x: Long): Long {
    var h: Long = x * LONG_PHI
    h = h xor (h ushr 32)
    return h xor (h ushr 16)
  }

  /** The inverse of [.mix]. This method is mainly useful to create unit tests.
   *
   * @param x a long integer.
   * @return a value that passed through [.mix] would give `x`.
   */
  fun invMix(x: Long): Long {
    var x = x
    x = x xor (x ushr 32)
    x = x xor (x ushr 16)
    return (x xor (x ushr 32)) * INV_LONG_PHI
  }


  /** Returns the hash code that would be returned by [Float.hashCode].
   *
   * @param f a float.
   * @return the same code as [new Float(f).hashCode()][Float.hashCode].
   */
  fun float2int(f: Float): Int {
    return f.toRawBits()
  }

  /** Returns the hash code that would be returned by [Double.hashCode].
   *
   * @param d a double.
   * @return the same code as [new Double(f).hashCode()][Double.hashCode].
   */
  fun double2int(d: Double): Int {
    val l: Long = d.toRawBits()
    return (l xor (l ushr 32)).toInt()
  }

  /** Returns the hash code that would be returned by [Long.hashCode].
   *
   * @param l a long.
   * @return the same code as [new Long(f).hashCode()][Long.hashCode].
   */
  fun long2int(l: Long): Int {
    return (l xor (l ushr 32)).toInt()
  }

  /** Returns the least power of two greater than or equal to the specified value.
   *
   *
   * Note that this function will return 1 when the argument is 0.
   *
   * @param x an integer smaller than or equal to 2<sup>30</sup>.
   * @return the least power of two greater than or equal to the specified value.
   */
  fun nextPowerOfTwo(x: Int): Int {
    return 1 shl (32 - (x - 1).countLeadingZeroBits())
  }

  /** Returns the least power of two greater than or equal to the specified value.
   *
   *
   * Note that this function will return 1 when the argument is 0.
   *
   * @param x a long integer smaller than or equal to 2<sup>62</sup>.
   * @return the least power of two greater than or equal to the specified value.
   */
  fun nextPowerOfTwo(x: Long): Long {
    return 1L shl (64 - (x - 1).countLeadingZeroBits())
  }


  /** Returns the maximum number of entries that can be filled before rehashing.
   *
   * @param n the size of the backing array.
   * @param f the load factor.
   * @return the maximum number of entries before rehashing.
   */
  fun maxFill(n: Int, f: Float): Int {/* We must guarantee that there is always at least
 * one free entry (even with pathological load factors). */
    return min(ceil((n * f).toDouble()).toInt().toDouble(), (n - 1).toDouble()).toInt()
  }

  /** Returns the maximum number of entries that can be filled before rehashing.
   *
   * @param n the size of the backing array.
   * @param f the load factor.
   * @return the maximum number of entries before rehashing.
   */
  fun maxFill(n: Long, f: Float): Long {/* We must guarantee that there is always at least
 * one free entry (even with pathological load factors). */
    return min(ceil((n * f).toDouble()).toLong().toDouble(), (n - 1).toDouble()).toLong()
  }

  /** Returns the least power of two smaller than or equal to 2<sup>30</sup> and larger than or equal to `Math.ceil(expected / f)`.
   *
   * @param expected the expected number of elements in a hash table.
   * @param f the load factor.
   * @return the minimum possible size for a backing array.
   * @throws IllegalArgumentException if the necessary size is larger than 2<sup>30</sup>.
   */
  fun arraySize(expected: Int, f: Float): Int {
    val s = max(2.0, nextPowerOfTwo(ceil((expected / f).toDouble()).toLong()).toDouble()).toLong()
    if (s > (1 shl 30)) throw IllegalArgumentException("Too large ($expected expected elements with load factor $f)")
    return s.toInt()
  }

  /** Returns the least power of two larger than or equal to `Math.ceil(expected / f)`.
   *
   * @param expected the expected number of elements in a hash table.
   * @param f the load factor.
   * @return the minimum possible size for a backing big array.
   */
  fun bigArraySize(expected: Long, f: Float): Long {
    return nextPowerOfTwo(ceil((expected / f).toDouble()).toLong())
  }
}