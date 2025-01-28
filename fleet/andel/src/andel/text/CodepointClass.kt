// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

private const val SEPARATORS = "`~!@#\$%^&*()-=+[{]}\\|;:'\",.<>/?"
private val maxSeparatorCode = SEPARATORS.maxBy { it.code }.code
private val separatorCodes = BooleanArray(maxSeparatorCode + 1).apply {
  SEPARATORS.forEach { this[it.code] = true }
}

enum class CodepointClass {
  CARET, SEPARATOR, NEWLINE, SPACE, UNDERSCORE, UPPERCASE, LOWERCASE
}

fun codepointClass(codepoint: Int): CodepointClass =
  when {
    codepoint == '\n'.code -> CodepointClass.NEWLINE
    codepoint == '\r'.code -> CodepointClass.NEWLINE
    codepoint == '_'.code -> CodepointClass.UNDERSCORE
    codepoint <= maxSeparatorCode && separatorCodes[codepoint] -> CodepointClass.SEPARATOR
    Character.isWhitespace(codepoint) -> CodepointClass.SPACE
    Character.isUpperCase(codepoint) -> CodepointClass.UPPERCASE
    else -> CodepointClass.LOWERCASE // treat all lowercase and unicode symbols as lowercase
  }

//todo this is wrong
internal fun charGeomLength(char: Char): Float {
  return when {
    char == '\n' -> 0f
    char == '\r' -> 0f
    char == '\t' -> 4f
    char.isLowSurrogate() -> 0f
    isFullWidth(char.code) -> 1.65f
    else -> 1f
  }
}

// Code points are derived from:
// https://unicode.org/Public/UNIDATA/EastAsianWidth.txt
fun isFullWidth(codePoint: Int): Boolean {
  return codePoint >= 0x1100 && (
    codePoint <= 0x115F || // Hangul Jamo
    codePoint == 0x2329 || // LEFT-POINTING ANGLE BRACKET
    codePoint == 0x232A || // RIGHT-POINTING ANGLE BRACKET
    // CJK Radicals Supplement .. Enclosed CJK Letters and Months
    (codePoint in 0x2E80..0x3247 && codePoint != 0x303F) ||
    // Enclosed CJK Letters and Months .. CJK Unified Ideographs Extension A
    (codePoint in 0x3250..0x4DBF) ||
    // CJK Unified Ideographs .. Yi Radicals
    (codePoint in 0x4E00..0xA4C6) ||
    // Hangul Jamo Extended-A
    (codePoint in 0xA960..0xA97C) ||
    // Hangul Syllables
    (codePoint in 0xAC00..0xD7A3) ||
    // CJK Compatibility Ideographs
    (codePoint in 0xF900..0xFAFF) ||
    // Vertical Forms
    (codePoint in 0xFE10..0xFE19) ||
    // CJK Compatibility Forms .. Small Form Variants
    (codePoint in 0xFE30..0xFE6B) ||
    // Halfwidth and Fullwidth Forms
    (codePoint in 0xFF01..0xFF60) ||
    (codePoint in 0xFFE0..0xFFE6) ||
    // Kana Supplement
    (codePoint in 0x1B000..0x1B001) ||
    // Enclosed Ideographic Supplement
    (codePoint in 0x1F200..0x1F251) ||
    // CJK Unified Ideographs Extension B .. Tertiary Ideographic Plane
    (codePoint in 0x20000..0x3FFFD))
}

fun isEmoji(codePoint: Int): Boolean {
  return (codePoint > 0x2600 &&
          (codePoint in 0x1F600..0x1F64F) || // Emoticons
          (codePoint in 0x1F300..0x1F5FF) || // Misc Symbols and Pictographs
          (codePoint in 0x1F680..0x1F6FF) || // Transport and Map
          (codePoint in 0x2600..0x26FF) ||   // Misc symbols
          // we are trying to copy alacrity behaviour here 
//          (codePoint in 0x2700..0x27BF) ||   // Dingbats
          (codePoint in 0xFE00..0xFE0F) ||   // Variation Selectors
          (codePoint in 0x1F900..0x1F9FF) || // Supplemental Symbols and Pictographs
          (codePoint in 0x1F1E6..0x1F1FF))   // Flags
}

fun isDoubleWidthCharacter(codePoint: Int, ambiguousIsDWC: Boolean = false): Boolean {
  return if (codePoint <= 0xa0 || codePoint > 0x452 && codePoint < 0x1100) {
    false
  }
  else mk_wcwidth(codePoint, ambiguousIsDWC) == 2
}

// The following code and data in converted from the https://www.cl.cam.ac.uk/~mgk25/ucs/wcwidth.c
// which can be treated a standard way to determine the width of a character


// The following code and data in converted from the https://www.cl.cam.ac.uk/~mgk25/ucs/wcwidth.c
// which can be treated a standard way to determine the width of a character
private val COMBINING = arrayOf(charArrayOf(0x0300.toChar(), 0x036F.toChar()), charArrayOf(0x0483.toChar(), 0x0486.toChar()),
                                charArrayOf(0x0488.toChar(), 0x0489.toChar()), charArrayOf(0x0591.toChar(), 0x05BD.toChar()),
                                charArrayOf(0x05BF.toChar(), 0x05BF.toChar()), charArrayOf(0x05C1.toChar(), 0x05C2.toChar()),
                                charArrayOf(0x05C4.toChar(), 0x05C5.toChar()), charArrayOf(0x05C7.toChar(), 0x05C7.toChar()),
                                charArrayOf(0x0600.toChar(), 0x0603.toChar()), charArrayOf(0x0610.toChar(), 0x0615.toChar()),
                                charArrayOf(0x064B.toChar(), 0x065E.toChar()), charArrayOf(0x0670.toChar(), 0x0670.toChar()),
                                charArrayOf(0x06D6.toChar(), 0x06E4.toChar()), charArrayOf(0x06E7.toChar(), 0x06E8.toChar()),
                                charArrayOf(0x06EA.toChar(), 0x06ED.toChar()), charArrayOf(0x070F.toChar(), 0x070F.toChar()),
                                charArrayOf(0x0711.toChar(), 0x0711.toChar()), charArrayOf(0x0730.toChar(), 0x074A.toChar()),
                                charArrayOf(0x07A6.toChar(), 0x07B0.toChar()), charArrayOf(0x07EB.toChar(), 0x07F3.toChar()),
                                charArrayOf(0x0901.toChar(), 0x0902.toChar()), charArrayOf(0x093C.toChar(), 0x093C.toChar()),
                                charArrayOf(0x0941.toChar(), 0x0948.toChar()), charArrayOf(0x094D.toChar(), 0x094D.toChar()),
                                charArrayOf(0x0951.toChar(), 0x0954.toChar()), charArrayOf(0x0962.toChar(), 0x0963.toChar()),
                                charArrayOf(0x0981.toChar(), 0x0981.toChar()), charArrayOf(0x09BC.toChar(), 0x09BC.toChar()),
                                charArrayOf(0x09C1.toChar(), 0x09C4.toChar()), charArrayOf(0x09CD.toChar(), 0x09CD.toChar()),
                                charArrayOf(0x09E2.toChar(), 0x09E3.toChar()), charArrayOf(0x0A01.toChar(), 0x0A02.toChar()),
                                charArrayOf(0x0A3C.toChar(), 0x0A3C.toChar()), charArrayOf(0x0A41.toChar(), 0x0A42.toChar()),
                                charArrayOf(0x0A47.toChar(), 0x0A48.toChar()), charArrayOf(0x0A4B.toChar(), 0x0A4D.toChar()),
                                charArrayOf(0x0A70.toChar(), 0x0A71.toChar()), charArrayOf(0x0A81.toChar(), 0x0A82.toChar()),
                                charArrayOf(0x0ABC.toChar(), 0x0ABC.toChar()), charArrayOf(0x0AC1.toChar(), 0x0AC5.toChar()),
                                charArrayOf(0x0AC7.toChar(), 0x0AC8.toChar()), charArrayOf(0x0ACD.toChar(), 0x0ACD.toChar()),
                                charArrayOf(0x0AE2.toChar(), 0x0AE3.toChar()), charArrayOf(0x0B01.toChar(), 0x0B01.toChar()),
                                charArrayOf(0x0B3C.toChar(), 0x0B3C.toChar()), charArrayOf(0x0B3F.toChar(), 0x0B3F.toChar()),
                                charArrayOf(0x0B41.toChar(), 0x0B43.toChar()), charArrayOf(0x0B4D.toChar(), 0x0B4D.toChar()),
                                charArrayOf(0x0B56.toChar(), 0x0B56.toChar()), charArrayOf(0x0B82.toChar(), 0x0B82.toChar()),
                                charArrayOf(0x0BC0.toChar(), 0x0BC0.toChar()), charArrayOf(0x0BCD.toChar(), 0x0BCD.toChar()),
                                charArrayOf(0x0C3E.toChar(), 0x0C40.toChar()), charArrayOf(0x0C46.toChar(), 0x0C48.toChar()),
                                charArrayOf(0x0C4A.toChar(), 0x0C4D.toChar()), charArrayOf(0x0C55.toChar(), 0x0C56.toChar()),
                                charArrayOf(0x0CBC.toChar(), 0x0CBC.toChar()), charArrayOf(0x0CBF.toChar(), 0x0CBF.toChar()),
                                charArrayOf(0x0CC6.toChar(), 0x0CC6.toChar()), charArrayOf(0x0CCC.toChar(), 0x0CCD.toChar()),
                                charArrayOf(0x0CE2.toChar(), 0x0CE3.toChar()), charArrayOf(0x0D41.toChar(), 0x0D43.toChar()),
                                charArrayOf(0x0D4D.toChar(), 0x0D4D.toChar()), charArrayOf(0x0DCA.toChar(), 0x0DCA.toChar()),
                                charArrayOf(0x0DD2.toChar(), 0x0DD4.toChar()), charArrayOf(0x0DD6.toChar(), 0x0DD6.toChar()),
                                charArrayOf(0x0E31.toChar(), 0x0E31.toChar()), charArrayOf(0x0E34.toChar(), 0x0E3A.toChar()),
                                charArrayOf(0x0E47.toChar(), 0x0E4E.toChar()), charArrayOf(0x0EB1.toChar(), 0x0EB1.toChar()),
                                charArrayOf(0x0EB4.toChar(), 0x0EB9.toChar()), charArrayOf(0x0EBB.toChar(), 0x0EBC.toChar()),
                                charArrayOf(0x0EC8.toChar(), 0x0ECD.toChar()), charArrayOf(0x0F18.toChar(), 0x0F19.toChar()),
                                charArrayOf(0x0F35.toChar(), 0x0F35.toChar()), charArrayOf(0x0F37.toChar(), 0x0F37.toChar()),
                                charArrayOf(0x0F39.toChar(), 0x0F39.toChar()), charArrayOf(0x0F71.toChar(), 0x0F7E.toChar()),
                                charArrayOf(0x0F80.toChar(), 0x0F84.toChar()), charArrayOf(0x0F86.toChar(), 0x0F87.toChar()),
                                charArrayOf(0x0F90.toChar(), 0x0F97.toChar()), charArrayOf(0x0F99.toChar(), 0x0FBC.toChar()),
                                charArrayOf(0x0FC6.toChar(), 0x0FC6.toChar()), charArrayOf(0x102D.toChar(), 0x1030.toChar()),
                                charArrayOf(0x1032.toChar(), 0x1032.toChar()), charArrayOf(0x1036.toChar(), 0x1037.toChar()),
                                charArrayOf(0x1039.toChar(), 0x1039.toChar()), charArrayOf(0x1058.toChar(), 0x1059.toChar()),
                                charArrayOf(0x1160.toChar(), 0x11FF.toChar()), charArrayOf(0x135F.toChar(), 0x135F.toChar()),
                                charArrayOf(0x1712.toChar(), 0x1714.toChar()), charArrayOf(0x1732.toChar(), 0x1734.toChar()),
                                charArrayOf(0x1752.toChar(), 0x1753.toChar()), charArrayOf(0x1772.toChar(), 0x1773.toChar()),
                                charArrayOf(0x17B4.toChar(), 0x17B5.toChar()), charArrayOf(0x17B7.toChar(), 0x17BD.toChar()),
                                charArrayOf(0x17C6.toChar(), 0x17C6.toChar()), charArrayOf(0x17C9.toChar(), 0x17D3.toChar()),
                                charArrayOf(0x17DD.toChar(), 0x17DD.toChar()), charArrayOf(0x180B.toChar(), 0x180D.toChar()),
                                charArrayOf(0x18A9.toChar(), 0x18A9.toChar()), charArrayOf(0x1920.toChar(), 0x1922.toChar()),
                                charArrayOf(0x1927.toChar(), 0x1928.toChar()), charArrayOf(0x1932.toChar(), 0x1932.toChar()),
                                charArrayOf(0x1939.toChar(), 0x193B.toChar()), charArrayOf(0x1A17.toChar(), 0x1A18.toChar()),
                                charArrayOf(0x1B00.toChar(), 0x1B03.toChar()), charArrayOf(0x1B34.toChar(), 0x1B34.toChar()),
                                charArrayOf(0x1B36.toChar(), 0x1B3A.toChar()), charArrayOf(0x1B3C.toChar(), 0x1B3C.toChar()),
                                charArrayOf(0x1B42.toChar(), 0x1B42.toChar()), charArrayOf(0x1B6B.toChar(), 0x1B73.toChar()),
                                charArrayOf(0x1DC0.toChar(), 0x1DCA.toChar()), charArrayOf(0x1DFE.toChar(), 0x1DFF.toChar()),
                                charArrayOf(0x200B.toChar(), 0x200F.toChar()), charArrayOf(0x202A.toChar(), 0x202E.toChar()),
                                charArrayOf(0x2060.toChar(), 0x2063.toChar()), charArrayOf(0x206A.toChar(), 0x206F.toChar()),
                                charArrayOf(0x20D0.toChar(), 0x20EF.toChar()), charArrayOf(0x302A.toChar(), 0x302F.toChar()),
                                charArrayOf(0x3099.toChar(), 0x309A.toChar()), charArrayOf(0xA806.toChar(), 0xA806.toChar()),
                                charArrayOf(0xA80B.toChar(), 0xA80B.toChar()), charArrayOf(0xA825.toChar(), 0xA826.toChar()),
                                charArrayOf(0xFB1E.toChar(), 0xFB1E.toChar()), charArrayOf(0xFE00.toChar(), 0xFE0F.toChar()),
                                charArrayOf(0xFE20.toChar(), 0xFE23.toChar()), charArrayOf(0xFEFF.toChar(), 0xFEFF.toChar()),
                                charArrayOf(0xFFF9.toChar(), 0xFFFB.toChar()))

private val AMBIGUOUS = arrayOf(charArrayOf(0x00A1.toChar(), 0x00A1.toChar()), charArrayOf(0x00A4.toChar(), 0x00A4.toChar()),
                                charArrayOf(0x00A7.toChar(), 0x00A8.toChar()), charArrayOf(0x00AA.toChar(), 0x00AA.toChar()),
                                charArrayOf(0x00AE.toChar(), 0x00AE.toChar()), charArrayOf(0x00B0.toChar(), 0x00B4.toChar()),
                                charArrayOf(0x00B6.toChar(), 0x00BA.toChar()), charArrayOf(0x00BC.toChar(), 0x00BF.toChar()),
                                charArrayOf(0x00C6.toChar(), 0x00C6.toChar()), charArrayOf(0x00D0.toChar(), 0x00D0.toChar()),
                                charArrayOf(0x00D7.toChar(), 0x00D8.toChar()), charArrayOf(0x00DE.toChar(), 0x00E1.toChar()),
                                charArrayOf(0x00E6.toChar(), 0x00E6.toChar()), charArrayOf(0x00E8.toChar(), 0x00EA.toChar()),
                                charArrayOf(0x00EC.toChar(), 0x00ED.toChar()), charArrayOf(0x00F0.toChar(), 0x00F0.toChar()),
                                charArrayOf(0x00F2.toChar(), 0x00F3.toChar()), charArrayOf(0x00F7.toChar(), 0x00FA.toChar()),
                                charArrayOf(0x00FC.toChar(), 0x00FC.toChar()), charArrayOf(0x00FE.toChar(), 0x00FE.toChar()),
                                charArrayOf(0x0101.toChar(), 0x0101.toChar()), charArrayOf(0x0111.toChar(), 0x0111.toChar()),
                                charArrayOf(0x0113.toChar(), 0x0113.toChar()), charArrayOf(0x011B.toChar(), 0x011B.toChar()),
                                charArrayOf(0x0126.toChar(), 0x0127.toChar()), charArrayOf(0x012B.toChar(), 0x012B.toChar()),
                                charArrayOf(0x0131.toChar(), 0x0133.toChar()), charArrayOf(0x0138.toChar(), 0x0138.toChar()),
                                charArrayOf(0x013F.toChar(), 0x0142.toChar()), charArrayOf(0x0144.toChar(), 0x0144.toChar()),
                                charArrayOf(0x0148.toChar(), 0x014B.toChar()), charArrayOf(0x014D.toChar(), 0x014D.toChar()),
                                charArrayOf(0x0152.toChar(), 0x0153.toChar()), charArrayOf(0x0166.toChar(), 0x0167.toChar()),
                                charArrayOf(0x016B.toChar(), 0x016B.toChar()), charArrayOf(0x01CE.toChar(), 0x01CE.toChar()),
                                charArrayOf(0x01D0.toChar(), 0x01D0.toChar()), charArrayOf(0x01D2.toChar(), 0x01D2.toChar()),
                                charArrayOf(0x01D4.toChar(), 0x01D4.toChar()), charArrayOf(0x01D6.toChar(), 0x01D6.toChar()),
                                charArrayOf(0x01D8.toChar(), 0x01D8.toChar()), charArrayOf(0x01DA.toChar(), 0x01DA.toChar()),
                                charArrayOf(0x01DC.toChar(), 0x01DC.toChar()), charArrayOf(0x0251.toChar(), 0x0251.toChar()),
                                charArrayOf(0x0261.toChar(), 0x0261.toChar()), charArrayOf(0x02C4.toChar(), 0x02C4.toChar()),
                                charArrayOf(0x02C7.toChar(), 0x02C7.toChar()), charArrayOf(0x02C9.toChar(), 0x02CB.toChar()),
                                charArrayOf(0x02CD.toChar(), 0x02CD.toChar()), charArrayOf(0x02D0.toChar(), 0x02D0.toChar()),
                                charArrayOf(0x02D8.toChar(), 0x02DB.toChar()), charArrayOf(0x02DD.toChar(), 0x02DD.toChar()),
                                charArrayOf(0x02DF.toChar(), 0x02DF.toChar()), charArrayOf(0x0391.toChar(), 0x03A1.toChar()),
                                charArrayOf(0x03A3.toChar(), 0x03A9.toChar()), charArrayOf(0x03B1.toChar(), 0x03C1.toChar()),
                                charArrayOf(0x03C3.toChar(), 0x03C9.toChar()), charArrayOf(0x0401.toChar(), 0x0401.toChar()),
                                charArrayOf(0x0410.toChar(), 0x044F.toChar()), charArrayOf(0x0451.toChar(), 0x0451.toChar()),
                                charArrayOf(0x2010.toChar(), 0x2010.toChar()), charArrayOf(0x2013.toChar(), 0x2016.toChar()),
                                charArrayOf(0x2018.toChar(), 0x2019.toChar()), charArrayOf(0x201C.toChar(), 0x201D.toChar()),
                                charArrayOf(0x2020.toChar(), 0x2022.toChar()), charArrayOf(0x2024.toChar(), 0x2027.toChar()),
                                charArrayOf(0x2030.toChar(), 0x2030.toChar()), charArrayOf(0x2032.toChar(), 0x2033.toChar()),
                                charArrayOf(0x2035.toChar(), 0x2035.toChar()), charArrayOf(0x203B.toChar(), 0x203B.toChar()),
                                charArrayOf(0x203E.toChar(), 0x203E.toChar()), charArrayOf(0x2074.toChar(), 0x2074.toChar()),
                                charArrayOf(0x207F.toChar(), 0x207F.toChar()), charArrayOf(0x2081.toChar(), 0x2084.toChar()),
                                charArrayOf(0x20AC.toChar(), 0x20AC.toChar()), charArrayOf(0x2103.toChar(), 0x2103.toChar()),
                                charArrayOf(0x2105.toChar(), 0x2105.toChar()), charArrayOf(0x2109.toChar(), 0x2109.toChar()),
                                charArrayOf(0x2113.toChar(), 0x2113.toChar()), charArrayOf(0x2116.toChar(), 0x2116.toChar()),
                                charArrayOf(0x2121.toChar(), 0x2122.toChar()), charArrayOf(0x2126.toChar(), 0x2126.toChar()),
                                charArrayOf(0x212B.toChar(), 0x212B.toChar()), charArrayOf(0x2153.toChar(), 0x2154.toChar()),
                                charArrayOf(0x215B.toChar(), 0x215E.toChar()), charArrayOf(0x2160.toChar(), 0x216B.toChar()),
                                charArrayOf(0x2170.toChar(), 0x2179.toChar()), charArrayOf(0x2190.toChar(), 0x2199.toChar()),
                                charArrayOf(0x21B8.toChar(), 0x21B9.toChar()), charArrayOf(0x21D2.toChar(), 0x21D2.toChar()),
                                charArrayOf(0x21D4.toChar(), 0x21D4.toChar()), charArrayOf(0x21E7.toChar(), 0x21E7.toChar()),
                                charArrayOf(0x2200.toChar(), 0x2200.toChar()), charArrayOf(0x2202.toChar(), 0x2203.toChar()),
                                charArrayOf(0x2207.toChar(), 0x2208.toChar()), charArrayOf(0x220B.toChar(), 0x220B.toChar()),
                                charArrayOf(0x220F.toChar(), 0x220F.toChar()), charArrayOf(0x2211.toChar(), 0x2211.toChar()),
                                charArrayOf(0x2215.toChar(), 0x2215.toChar()), charArrayOf(0x221A.toChar(), 0x221A.toChar()),
                                charArrayOf(0x221D.toChar(), 0x2220.toChar()), charArrayOf(0x2223.toChar(), 0x2223.toChar()),
                                charArrayOf(0x2225.toChar(), 0x2225.toChar()), charArrayOf(0x2227.toChar(), 0x222C.toChar()),
                                charArrayOf(0x222E.toChar(), 0x222E.toChar()), charArrayOf(0x2234.toChar(), 0x2237.toChar()),
                                charArrayOf(0x223C.toChar(), 0x223D.toChar()), charArrayOf(0x2248.toChar(), 0x2248.toChar()),
                                charArrayOf(0x224C.toChar(), 0x224C.toChar()), charArrayOf(0x2252.toChar(), 0x2252.toChar()),
                                charArrayOf(0x2260.toChar(), 0x2261.toChar()), charArrayOf(0x2264.toChar(), 0x2267.toChar()),
                                charArrayOf(0x226A.toChar(), 0x226B.toChar()), charArrayOf(0x226E.toChar(), 0x226F.toChar()),
                                charArrayOf(0x2282.toChar(), 0x2283.toChar()), charArrayOf(0x2286.toChar(), 0x2287.toChar()),
                                charArrayOf(0x2295.toChar(), 0x2295.toChar()), charArrayOf(0x2299.toChar(), 0x2299.toChar()),
                                charArrayOf(0x22A5.toChar(), 0x22A5.toChar()), charArrayOf(0x22BF.toChar(), 0x22BF.toChar()),
                                charArrayOf(0x2312.toChar(), 0x2312.toChar()), charArrayOf(0x2460.toChar(), 0x24E9.toChar()),
                                charArrayOf(0x24EB.toChar(), 0x254B.toChar()), charArrayOf(0x2550.toChar(), 0x2573.toChar()),
                                charArrayOf(0x2580.toChar(), 0x258F.toChar()), charArrayOf(0x2592.toChar(), 0x2595.toChar()),
                                charArrayOf(0x25A0.toChar(), 0x25A1.toChar()), charArrayOf(0x25A3.toChar(), 0x25A9.toChar()),
                                charArrayOf(0x25B2.toChar(), 0x25B3.toChar()), charArrayOf(0x25B6.toChar(), 0x25B7.toChar()),
                                charArrayOf(0x25BC.toChar(), 0x25BD.toChar()), charArrayOf(0x25C0.toChar(), 0x25C1.toChar()),
                                charArrayOf(0x25C6.toChar(), 0x25C8.toChar()), charArrayOf(0x25CB.toChar(), 0x25CB.toChar()),
                                charArrayOf(0x25CE.toChar(), 0x25D1.toChar()), charArrayOf(0x25E2.toChar(), 0x25E5.toChar()),
                                charArrayOf(0x25EF.toChar(), 0x25EF.toChar()), charArrayOf(0x2605.toChar(), 0x2606.toChar()),
                                charArrayOf(0x2609.toChar(), 0x2609.toChar()), charArrayOf(0x260E.toChar(), 0x260F.toChar()),
                                charArrayOf(0x2614.toChar(), 0x2615.toChar()), charArrayOf(0x261C.toChar(), 0x261C.toChar()),
                                charArrayOf(0x261E.toChar(), 0x261E.toChar()), charArrayOf(0x2640.toChar(), 0x2640.toChar()),
                                charArrayOf(0x2642.toChar(), 0x2642.toChar()), charArrayOf(0x2660.toChar(), 0x2661.toChar()),
                                charArrayOf(0x2663.toChar(), 0x2665.toChar()), charArrayOf(0x2667.toChar(), 0x266A.toChar()),
                                charArrayOf(0x266C.toChar(), 0x266D.toChar()), charArrayOf(0x266F.toChar(), 0x266F.toChar()),
                                charArrayOf(0x273D.toChar(), 0x273D.toChar()), charArrayOf(0x2776.toChar(), 0x277F.toChar()),
                                charArrayOf(0xE000.toChar(), 0xF8FF.toChar()), charArrayOf(0xFFFD.toChar(), 0xFFFD.toChar()))


/* auxiliary function for binary search in interval table */
private fun bisearch(ucs: Char, table: Array<CharArray>, max: Int): Int {
  var max = max
  var min = 0
  var mid: Int
  if (ucs < table[0][0] || ucs > table[max][1]) return 0
  while (max >= min) {
    mid = (min + max) / 2
    if (ucs > table[mid][1]) min = mid + 1 else if (ucs < table[mid][0]) max = mid - 1 else return 1
  }
  return 0
}

private fun mk_wcwidth(ucs: Int, ambiguousIsDoubleWidth: Boolean): Int {
  /* sorted list of non-overlapping intervals of non-spacing characters */
  /* generated by "uniset +cat=Me +cat=Mn +cat=Cf -00AD +1160-11FF +200B c" */

  /* test for8-bnew char[]it control characters */
  if (ucs == 0) return 0
  if (ucs < 32 || ucs >= 0x7f && ucs < 0xa0) return -1
  if (ambiguousIsDoubleWidth) {
    if (bisearch(ucs.toChar(), AMBIGUOUS, AMBIGUOUS.size - 1) > 0) {
      return 2
    }
  }


  /* binary search in table of non-spacing characters */return if (bisearch(ucs.toChar(), COMBINING, COMBINING.size - 1) > 0) {
    0
  }
  else 1 +
       if (ucs >= 0x1100 &&
           (ucs <= 0x115f || ucs == 0x2329 || ucs == 0x232a || ucs >= 0x2e80 && ucs <= 0xa4cf && ucs != 0x303f || ucs >= 0xac00 && ucs <= 0xd7a3 || ucs >= 0xf900 && ucs <= 0xfaff || ucs >= 0xfe10 && ucs <= 0xfe19 || ucs >= 0xfe30 && ucs <= 0xfe6f || ucs >= 0xff00 && ucs <= 0xff60 || ucs >= 0xffe0 && ucs <= 0xffe6 || ucs >= 0x20000 && ucs <= 0x2fffd || ucs >= 0x30000 && ucs <= 0x3fffd)) 1
       else 0

  /* if we arrive here, ucs is not a combining or C0/C1 control character */
}