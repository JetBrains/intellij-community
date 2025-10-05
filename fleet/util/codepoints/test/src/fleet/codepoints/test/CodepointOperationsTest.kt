package fleet.codepoints.test

import fleet.codepoints.Codepoint
import fleet.codepoints.isCodepointInRanges
import kotlin.test.Test
import kotlin.test.fail

class CodepointOperationsTest {
  @Test
  fun isJavaIdentifierStart() {
    assertMatchJvmImplementation(Codepoint::isJavaIdentifierStart, Character::isJavaIdentifierStart, "isJavaIdentifierStart")
  }

  @Test
  fun isJavaIdentifierPart() {
    assertMatchJvmImplementation(Codepoint::isJavaIdentifierPart, Character::isJavaIdentifierPart, "isJavaIdentifierPart")
  }

  @Test
  fun isSpaceChar() {
    assertMatchJvmImplementation(Codepoint::isSpaceChar, Character::isSpaceChar, "isSpaceChar")
  }

  @Test
  fun isIsoControl() {
    assertMatchJvmImplementation(Codepoint::isISOControl, Character::isISOControl, "isIsoControl")
  }

  @Test
  fun isKatakana() {
    assertMatchJvmImplementation(Codepoint::isKatakana, { Character.UnicodeScript.of(it) == Character.UnicodeScript.KATAKANA }, "isKatakana")
  }

  @Test
  fun isHiragana() {
    assertMatchJvmImplementation(Codepoint::isHiragana, { Character.UnicodeScript.of(it) == Character.UnicodeScript.HIRAGANA }, "isHiragana")
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun assertMatchJvmImplementation(
    multiplatform: (Int) -> Boolean,
    jvm: (Int) -> Boolean,
    name: String,
    exclusions: IntArray = intArrayOf(),
  ) {
    val hexFormat = HexFormat {
      number {
        prefix = "0x"
        upperCase = true
        removeLeadingZeros = true
      }
    }
    val errors = buildList {
      (Character.MIN_CODE_POINT..Character.MAX_CODE_POINT).forEach { codePoint ->
        val multiplatformResult = multiplatform(codePoint)
        val jvmResult = jvm(codePoint)
        val char = codePoint.toHexString(hexFormat)
        val excluded = isCodepointInRanges(codePoint, exclusions)
        if (multiplatformResult != jvmResult) {
          if (!excluded) {
            add("multiplatform implementation of $name does not match JVM implementation for codepoint $char. JVM: $jvmResult, multiplatform: $multiplatformResult")
          }
        }
        else if (excluded) {
          add("multiplatform implementation is supposed to be different. Remove $char from exclusions if it's fixed")
        }
      }
    }
    if (errors.isNotEmpty()) {
      errors.forEach { println(it) }
      fail("Failed with ${errors.size} mismatches")
    }
  }
}