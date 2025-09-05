package fleet.codepoints.test

import fleet.codepoints.Codepoint
import org.junit.Ignore
import org.junit.Test
import kotlin.test.fail

/**
 * Some of these tests may break if the JVM implementation/version change.
 *
 * @see fleet.codepoints.eitherUnicodeOrJavaStartIdRanges to be synced with JVM (see comments there)
 */
class CodepointOperationsTest {
  @Ignore
  @Test
  fun isUnicodeIdentifierPart() {
    assertMatchJvmImplementation(Codepoint::isUnicodeIdentifierPart, Character::isUnicodeIdentifierPart, "isUnicodeIdentifierPart")
  }

  @Test
  fun isSpaceChar() {
    assertMatchJvmImplementation(Codepoint::isSpaceChar, Character::isSpaceChar, "isSpaceChar")
  }

  @Ignore
  @Test
  fun isJavaIdentifierPart() {
    assertMatchJvmImplementation(Codepoint::isJavaIdentifierPart, Character::isJavaIdentifierPart, "isJavaIdentifierPart")
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

  @Test
  fun isCommonScript() {
    assertMatchJvmImplementation(
      Codepoint::isCommonScript, { Character.UnicodeScript.of(it) == Character.UnicodeScript.COMMON }, "isCommonScript",
      exclusions = listOf(
        0x2427..0x2429, // 23E2..2429    ; Common # So  [72] WHITE TRAPEZIUM..SYMBOL FOR DELETE MEDIUM SHADE FORM
        0x2FFC..0x2FFF, // 2FF0..2FFF    ; Common # So  [16] IDEOGRAPHIC DESCRIPTION CHARACTER LEFT TO RIGHT..IDEOGRAPHIC DESCRIPTION CHARACTER ROTATION
        0x31E4..0x31E5, // 31C0..31E5    ; Common # So  [38] CJK STROKE T..CJK STROKE SZP
        0x31EF..0x31EF, // 31EF          ; Common # So       IDEOGRAPHIC DESCRIPTION CHARACTER SUBTRACTION
        0x1CC00..0x1CCEF, // 1CC00..1CCEF  ; Common # So [240] UP-POINTING GO-KART..OUTLINED LATIN CAPITAL LETTER Z
        0x1CCF0..0x1CCF9, // 1CCF0..1CCF9  ; Common # Nd  [10] OUTLINED DIGIT ZERO..OUTLINED DIGIT NINE
        0x1CD00..0x1CEB3, // 1CD00..1CEB3  ; Common # So [436] BLOCK OCTANT-3..BLACK RIGHT TRIANGLE CARET
        0x1F8B2..0x1F8BB, // 1F8B0..1F8BB  ; Common # So  [12] ARROW POINTING UPWARDS THEN NORTH WEST..SOUTH WEST ARROW FROM BAR
        0x1F8C0..0x1F8C1, // 1F8C0..1F8C1  ; Common # So   [2] LEFTWARDS ARROW FROM DOWNWARDS ARROW..RIGHTWARDS ARROW FROM DOWNWARDS ARROW
        0x1FA89..0x1FA89, // 1FA80..1FA89  ; Common # So  [10] YO-YO..HARP
        0x1FA8F..0x1FA8F, // 1FA8F..1FAC6  ; Common # So  [56] SHOVEL..FINGERPRINT
        0x1FABE..0x1FABE, // 1FA8F..1FAC6  ; Common # So  [56] SHOVEL..FINGERPRINT
        0x1FAC6..0x1FAC6, // 1FA8F..1FAC6  ; Common # So  [56] SHOVEL..FINGERPRINT
        0x1FADC..0x1FADC, // 1FACE..1FADC  ; Common # So  [15] MOOSE..ROOT VEGETABLE,
        0x1FADF..0x1FADF, // 1FADF..1FAE9  ; Common # So  [11] SPLATTER..FACE WITH BAGS UNDER EYES
        0x1FAE9..0x1FAE9, // 1FADF..1FAE9  ; Common # So  [11] SPLATTER..FACE WITH BAGS UNDER EYES
        0x1FBCB..0x1FBEF, //1FB94..1FBEF  ; Common # So  [92] LEFT HALF INVERSE MEDIUM SHADE AND RIGHT HALF BLOCK..TOP LEFT JUSTIFIED LOWER RIGHT QUARTER BLACK CIRCLE
      ),
    )
  }

  @Test
  fun isIdeographic() {
    assertMatchJvmImplementation(Codepoint::isIdeographic, Character::isIdeographic, "isIdeographic",
                                 exclusions = listOf(
                                   0x18CFF..0x18CFF, // 18CFF..18D08  ; Ideographic # Lo  [10] KHITAN SMALL SCRIPT CHARACTER-18CFF..TANGUT IDEOGRAPH-18D08
                                   0x2EBF0..0x2EE5D // 2EBF0..2EE5D  ; Ideographic # Lo [622] CJK UNIFIED IDEOGRAPH-2EBF0..CJK UNIFIED IDEOGRAPH-2EE5D
                                 ))
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun assertMatchJvmImplementation(
    multiplatform: (Int) -> Boolean,
    jvm: (Int) -> Boolean,
    name: String,
    exclusions: List<IntRange> = emptyList(),
  ) {
    val exclusionRangeStarts = exclusions.map { it.first }.toIntArray()
    val exclusionRangeEnds = exclusions.map { it.last }.toIntArray()

    val errors = buildList {
      (Character.MIN_CODE_POINT..Character.MAX_CODE_POINT).forEach { codePoint ->
        val multiplatformResult = multiplatform(codePoint)
        val jvmResult = jvm(codePoint)
        val char = codePoint.toHexString(HexFormat {
          number {
            prefix = "0x"
            upperCase = true
            removeLeadingZeros = true
          }
        })
        val excluded = Codepoint.binarySearchInRanges(codePoint, exclusionRangeStarts, exclusionRangeEnds)
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
      fail(errors.joinToString("\n"))
    }
  }
}