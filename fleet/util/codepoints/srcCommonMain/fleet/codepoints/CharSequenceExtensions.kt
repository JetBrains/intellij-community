package fleet.codepoints

fun CharSequence.codePointAt(index: Int): Codepoint {
  if (index !in indices) throw IndexOutOfBoundsException("Index out of range: $index, size: $length")

  val firstChar = this[index]
  if (firstChar.isHighSurrogate() && index + 1 < length) {
    val secondChar = this[index + 1]
    if (secondChar.isLowSurrogate()) {
      return Codepoint.fromChars(firstChar, secondChar)
    }
  }
  return Codepoint(firstChar.code)
}

fun CharSequence.codePointBefore(index: Int): Codepoint {
  val startIndex = index - 1
  if (startIndex < 0 || index !in indices) throw IndexOutOfBoundsException("Index out of range: $index, size: $length")

  val secondChar = this[startIndex]
  if (secondChar.isLowSurrogate() && startIndex - 1 >= 0) {
    val firstChar = this[startIndex - 1]
    if (firstChar.isHighSurrogate()) {
      return Codepoint.fromChars(firstChar, secondChar)
    }
  }

  return Codepoint(secondChar.code)
}

fun CharSequence.codepoints(offset: Int, direction: Direction = Direction.FORWARD): Iterator<Codepoint> =
  when (direction) {
    Direction.FORWARD -> iterator {
      var i = offset
      val len = length
      while (i < len) {
        val c1 = get(i++)
        if (c1.isHighSurrogate()) {
          if (i < len) {
            val c2 = get(i++)
            if (c2.isLowSurrogate()) {
              yield(Codepoint.fromChars(c1, c2))
            }
          }
        }
        else {
          yield(Codepoint(c1.code))
        }
      }
    }
    Direction.BACKWARD -> iterator {
      var i = offset - 1
      while (i >= 0) {
        val c2 = get(i--)
        if (c2.isLowSurrogate()) {
          if (i >= 0) {
            val c1 = get(i--)
            if (c1.isHighSurrogate()) {
              yield(Codepoint.fromChars(c1, c2))
            }
          }
        }
        else {
          yield(Codepoint(c2.code))
        }
      }
    }
  }

enum class Direction {
  FORWARD,
  BACKWARD,
}