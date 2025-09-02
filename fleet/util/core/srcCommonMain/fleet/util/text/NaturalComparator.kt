// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text

/**
 * Implementation of
 * ["Sorting for Humans: Natural Sort Order"](https://blog.codinghorror.com/sorting-for-humans-natural-sort-order/).
 *
 * Copied from com.intellij.openapi.util.text.NaturalComparator
 */
object NaturalComparator : Comparator<String?> {
  override fun compare(s1: String?, s2: String?): Int {
    if (s1 == s2) {
      return 0
    }
    if (s1 == null) return -1
    if (s2 == null) return +1
    return naturalCompare(s1, s2, s1.length, s2.length, true, false)
  }

  private fun naturalCompare(
    s1: String,
    s2: String,
    length1: Int,
    length2: Int,
    ignoreCase: Boolean,
    likeFileNames: Boolean,
  ): Int {
    var i = 0
    var j = 0
    while (i < length1 && j < length2) {
      val ch1 = s1[i]
      val ch2 = s2[j]
      if ((ch1.isDigit() || ch1 == ' ') && (ch2.isDigit() || ch2 == ' ')) {
        val start1 = skipChar(s1, skipChar(s1, i, length1, ' '), length1, '0')
        val start2 = skipChar(s2, skipChar(s2, j, length2, ' '), length2, '0')

        val end1 = skipDigits(s1, start1, length1)
        val end2 = skipDigits(s2, start2, length2)

        // numbers with more digits are always greater than shorter numbers
        val lengthDiff = (end1 - start1) - (end2 - start2)
        if (lengthDiff != 0) return lengthDiff

        // compare numbers with equal digit count
        val numberDiff = compareCharRange(s1, s2, start1, start2, end1)
        if (numberDiff != 0) return numberDiff

        // compare number length including leading spaces and zeroes
        val fullLengthDiff = (end1 - i) - (end2 - j)
        if (fullLengthDiff != 0) return fullLengthDiff

        // the numbers are the same; compare leading spaces and zeroes
        val leadingDiff = compareCharRange(s1, s2, i, j, start1)
        if (leadingDiff != 0) return leadingDiff

        i = end1 - 1
        j = end2 - 1
      }
      else if (likeFileNames) {
        //for supernatural comparison (IDEA-80435)
        if (ch1 != ch2) {
          val diff = when {
            ch1 == '-' && ch2 != '_' -> compareChars('_', ch2, ignoreCase)
            ch2 == '-' && ch1 != '_' -> compareChars(ch1, '_', ignoreCase)
            else -> compareChars(ch1, ch2, ignoreCase)
          }
          if (diff != 0) return diff
        }
      }
      else {
        val diff = compareChars(ch1, ch2, ignoreCase)
        if (diff != 0) return diff
      }
      i++
      j++
    }

    // After the loop, the end of one of the strings might not have been reached if the other
    // string ends with a number and the strings are equal until the end of that number.
    // When there are more characters in the string, then it is greater.
    return when {
      i < length1 -> +1
      j < length2 -> -1
      length1 != length2 -> length1 - length2

      // do case-sensitive compare if case-insensitive strings are equal
      ignoreCase -> naturalCompare(s1, s2, length1, length2, false, likeFileNames)
      else -> 0
    }
  }

  private fun compareCharRange(s1: String, s2: String, offset1: Int, offset2: Int, end1: Int): Int {
    var i = offset1
    var j = offset2
    while (i < end1) {
      val diff = s1[i].code - s2[j].code
      if (diff != 0) return diff
      i++
      j++
    }
    return 0
  }

  private fun compareChars(ch1: Char, ch2: Char, ignoreCase: Boolean): Int {
    // transitivity fix, otherwise can fail when comparing strings with characters between ' ' and '0' (e.g. '#')
    if (ch1 == ' ' && ch2 > ' ' && ch2 < '0') return +1
    if (ch2 == ' ' && ch1 > ' ' && ch1 < '0') return -1

    return if (ignoreCase) ch1.compareToIgnoreCase(ch2) else ch1.compareTo(ch2)
  }

  private fun skipDigits(s: String, start: Int, end: Int): Int {
    var start = start
    while (start < end && s[start].isDigit()) start++
    return start
  }

  private fun skipChar(s: String, start: Int, end: Int, c: Char): Int {
    var start = start
    while (start < end && s[start] == c) start++
    return start
  }
}

private fun Char.compareToIgnoreCase(other: Char): Int {
  // duplicating String.equalsIgnoreCase logic / com.intellij.openapi.util.text.Strings.compare(char, char, boolean)
  var diff = this - other
  if (diff == 0) {
    return 0
  }
  // If characters don't match but case may be ignored,
  // try converting both characters to uppercase.
  // If the results match, then the comparison scan should
  // continue.
  val u1 = this.uppercaseChar()
  val u2 = other.uppercaseChar()
  diff = u1 - u2
  if (diff != 0) {
    // Unfortunately, conversion to uppercase does not work properly
    // for the Georgian alphabet, which has strange rules about case
    // conversion.  So we need to make one last check before
    // exiting.
    diff = u1.lowercaseChar() - u2.lowercaseChar()
  }
  return diff
}
