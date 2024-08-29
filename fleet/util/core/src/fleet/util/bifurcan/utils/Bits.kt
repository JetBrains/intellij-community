// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.bifurcan.utils

/**
 * @author ztellman
 */
object Bits {
  private val deBruijnIndex = byteArrayOf(
    0, 1, 2, 53, 3, 7, 54, 27, 4, 38, 41, 8, 34, 55, 48, 28,
    62, 5, 39, 46, 44, 42, 22, 9, 24, 35, 59, 56, 49, 18, 29, 11,
    63, 52, 6, 26, 37, 40, 33, 47, 61, 45, 43, 21, 23, 58, 17, 10,
    51, 25, 36, 32, 60, 20, 57, 16, 50, 31, 19, 15, 30, 14, 13, 12
  )

  /**
   * @param n a number, which must be a power of two
   * @return the offset of the bit
   */
  fun bitOffset(n: Long): Int {
    return deBruijnIndex[0xFF and ((n * 0x022fdd63cc95386dL) ushr 58).toInt()]
      .toInt()
  }

  /**
   * @param n a number
   * @return the same number, with all but the lowest bit zeroed out
   */
  fun lowestBit(n: Long): Long {
    return n and -n
  }

  /**
   * @param n a number
   * @return the same number, with all but the lowest bit zeroed out
   */
  fun lowestBit(n: Int): Int {
    return n and -n
  }

  /**
   * @param n a number
   * @return the same number, with all but the highest bit zeroed out
   */
  fun highestBit(n: Long): Long {
    return n.takeHighestOneBit()
    // return java.lang.Long.highestOneBit(n)
  }

  /**
   * @param n a number
   * @return the same number, with all but the highest bit zeroed out
   */
  fun highestBit(n: Int): Int {
    return n.takeHighestOneBit()
    // return Integer.highestOneBit(n)
  }

  /**
   * @param n a number
   * @return the log2 of that value, rounded down
   */
  fun log2Floor(n: Long): Int {
    return bitOffset(highestBit(n))
  }

  /**
   * @param n a number
   * @return the log2 of the value, rounded up
   */
  fun log2Ceil(n: Long): Int {
    val log2 = log2Floor(n)
    return if (isPowerOfTwo(n)) log2 else log2 + 1
  }


  /**
   * @param bits a bit offset
   * @return a mask, with all bits below that offset set to one
   */
  fun maskBelow(bits: Int): Long {
    return (1L shl bits) - 1
  }

  /**
   * @param bits a bit offset
   * @return a mask, with all bits above that offset set to one
   */
  fun maskAbove(bits: Int): Long {
    return -1L and maskBelow(bits).inv()
  }

  /**
   * @return the offset of the highest bit which differs between `a` and `b`
   */
  fun branchingBit(a: Long, b: Long): Int {
    return if (a == b) {
      -1
    }
    else {
      bitOffset(highestBit(a xor b))
    }
  }

  /**
   * @param n a number
   * @return true, if the number is a power of two
   */
  fun isPowerOfTwo(n: Long): Boolean {
    return (n and (n - 1)) == 0L
  }

  fun slice(n: Long, start: Int, end: Int): Long {
    return (n shr start) and maskBelow(end - start)
  }
}