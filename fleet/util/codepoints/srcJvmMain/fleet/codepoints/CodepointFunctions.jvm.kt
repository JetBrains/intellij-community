package fleet.codepoints

import fleet.util.multiplatform.Actual

@Actual internal fun codepointsToStringJvm(vararg codepoints: Int): String = java.lang.String(codepoints, 0, codepoints.size).toString()
@Actual internal fun codepointOfJvm(highSurrogate: Char, lowSurrogate: Char): Codepoint = Codepoint(Character.toCodePoint(highSurrogate, lowSurrogate))
@Actual internal fun highSurrogateJvm(codepoint: Int): Char = Character.highSurrogate(codepoint)
@Actual internal fun lowSurrogateJvm(codepoint: Int): Char = Character.lowSurrogate(codepoint)
@Actual internal fun isLetterJvm(codepoint: Int): Boolean = Character.isLetter(codepoint)
@Actual internal fun isDigitJvm(codepoint: Int): Boolean = Character.isDigit(codepoint)
@Actual internal fun isLetterOrDigitJvm(codepoint: Int): Boolean = Character.isLetterOrDigit(codepoint)
@Actual internal fun isUpperCaseJvm(codepoint: Int): Boolean = Character.isUpperCase(codepoint)
@Actual internal fun isLowerCaseJvm(codepoint: Int): Boolean = Character.isLowerCase(codepoint)
@Actual internal fun toLowerCaseJvm(codepoint: Int): Int = Character.toLowerCase(codepoint)
@Actual internal fun toUpperCaseJvm(codepoint: Int): Int = Character.toUpperCase(codepoint)
@Actual internal fun isSpaceCharJvm(codepoint: Int): Boolean = Character.isSpaceChar(codepoint)
@Actual internal fun isWhitespaceJvm(codepoint: Int): Boolean = Character.isWhitespace(codepoint)
@Actual internal fun isIdeographicJvm(codepoint: Int): Boolean = Character.isIdeographic(codepoint)
@Actual internal fun isIdentifierIgnorableJvm(codepoint: Int): Boolean = Character.isIdentifierIgnorable(codepoint)
@Actual internal fun isUnicodeIdentifierStartJvm(codepoint: Int): Boolean = Character.isUnicodeIdentifierStart(codepoint)
@Actual internal fun isUnicodeIdentifierPartJvm(codepoint: Int): Boolean = Character.isUnicodeIdentifierPart(codepoint)
@Actual internal fun isJavaIdentifierStartJvm(codepoint: Int): Boolean = Character.isJavaIdentifierStart(codepoint)
@Actual internal fun isJavaIdentifierPartJvm(codepoint: Int): Boolean = Character.isJavaIdentifierPart(codepoint)
@Actual internal fun isISOControlJvm(codepoint: Int): Boolean = Character.isISOControl(codepoint)
@Actual internal fun getUnicodeScriptJvm(codepoint: Int): UnicodeScript = jvmScriptToUnicodeScript(Character.UnicodeScript.of(codepoint))

private fun jvmScriptToUnicodeScript(jvmScript: Character.UnicodeScript): UnicodeScript = when (jvmScript) {
  Character.UnicodeScript.UNKNOWN -> UnicodeScript.UNKNOWN
  Character.UnicodeScript.COMMON -> UnicodeScript.COMMON
  Character.UnicodeScript.LATIN -> UnicodeScript.LATIN
  Character.UnicodeScript.BOPOMOFO -> UnicodeScript.BOPOMOFO
  Character.UnicodeScript.INHERITED -> UnicodeScript.INHERITED
  Character.UnicodeScript.GREEK -> UnicodeScript.GREEK
  Character.UnicodeScript.COPTIC -> UnicodeScript.COPTIC
  Character.UnicodeScript.CYRILLIC -> UnicodeScript.CYRILLIC
  Character.UnicodeScript.ARMENIAN -> UnicodeScript.ARMENIAN
  Character.UnicodeScript.HEBREW -> UnicodeScript.HEBREW
  Character.UnicodeScript.ARABIC -> UnicodeScript.ARABIC
  Character.UnicodeScript.SYRIAC -> UnicodeScript.SYRIAC
  Character.UnicodeScript.THAANA -> UnicodeScript.THAANA
  Character.UnicodeScript.NKO -> UnicodeScript.NKO
  Character.UnicodeScript.SAMARITAN -> UnicodeScript.SAMARITAN
  Character.UnicodeScript.MANDAIC -> UnicodeScript.MANDAIC
  Character.UnicodeScript.DEVANAGARI -> UnicodeScript.DEVANAGARI
  Character.UnicodeScript.BENGALI -> UnicodeScript.BENGALI
  Character.UnicodeScript.GURMUKHI -> UnicodeScript.GURMUKHI
  Character.UnicodeScript.GUJARATI -> UnicodeScript.GUJARATI
  Character.UnicodeScript.ORIYA -> UnicodeScript.ORIYA
  Character.UnicodeScript.TAMIL -> UnicodeScript.TAMIL
  Character.UnicodeScript.TELUGU -> UnicodeScript.TELUGU
  Character.UnicodeScript.KANNADA -> UnicodeScript.KANNADA
  Character.UnicodeScript.MALAYALAM -> UnicodeScript.MALAYALAM
  Character.UnicodeScript.SINHALA -> UnicodeScript.SINHALA
  Character.UnicodeScript.THAI -> UnicodeScript.THAI
  Character.UnicodeScript.LAO -> UnicodeScript.LAO
  Character.UnicodeScript.TIBETAN -> UnicodeScript.TIBETAN
  Character.UnicodeScript.MYANMAR -> UnicodeScript.MYANMAR
  Character.UnicodeScript.GEORGIAN -> UnicodeScript.GEORGIAN
  Character.UnicodeScript.HANGUL -> UnicodeScript.HANGUL
  Character.UnicodeScript.ETHIOPIC -> UnicodeScript.ETHIOPIC
  Character.UnicodeScript.CHEROKEE -> UnicodeScript.CHEROKEE
  Character.UnicodeScript.CANADIAN_ABORIGINAL -> UnicodeScript.CANADIAN_ABORIGINAL
  Character.UnicodeScript.OGHAM -> UnicodeScript.OGHAM
  Character.UnicodeScript.RUNIC -> UnicodeScript.RUNIC
  Character.UnicodeScript.TAGALOG -> UnicodeScript.TAGALOG
  Character.UnicodeScript.HANUNOO -> UnicodeScript.HANUNOO
  Character.UnicodeScript.BUHID -> UnicodeScript.BUHID
  Character.UnicodeScript.TAGBANWA -> UnicodeScript.TAGBANWA
  Character.UnicodeScript.KHMER -> UnicodeScript.KHMER
  Character.UnicodeScript.MONGOLIAN -> UnicodeScript.MONGOLIAN
  Character.UnicodeScript.LIMBU -> UnicodeScript.LIMBU
  Character.UnicodeScript.TAI_LE -> UnicodeScript.TAI_LE
  Character.UnicodeScript.NEW_TAI_LUE -> UnicodeScript.NEW_TAI_LUE
  Character.UnicodeScript.BUGINESE -> UnicodeScript.BUGINESE
  Character.UnicodeScript.TAI_THAM -> UnicodeScript.TAI_THAM
  Character.UnicodeScript.BALINESE -> UnicodeScript.BALINESE
  Character.UnicodeScript.SUNDANESE -> UnicodeScript.SUNDANESE
  Character.UnicodeScript.BATAK -> UnicodeScript.BATAK
  Character.UnicodeScript.LEPCHA -> UnicodeScript.LEPCHA
  Character.UnicodeScript.OL_CHIKI -> UnicodeScript.OL_CHIKI
  Character.UnicodeScript.BRAILLE -> UnicodeScript.BRAILLE
  Character.UnicodeScript.GLAGOLITIC -> UnicodeScript.GLAGOLITIC
  Character.UnicodeScript.TIFINAGH -> UnicodeScript.TIFINAGH
  Character.UnicodeScript.HAN -> UnicodeScript.HAN
  Character.UnicodeScript.HIRAGANA -> UnicodeScript.HIRAGANA
  Character.UnicodeScript.KATAKANA -> UnicodeScript.KATAKANA
  Character.UnicodeScript.YI -> UnicodeScript.YI
  Character.UnicodeScript.LISU -> UnicodeScript.LISU
  Character.UnicodeScript.VAI -> UnicodeScript.VAI
  Character.UnicodeScript.BAMUM -> UnicodeScript.BAMUM
  Character.UnicodeScript.SYLOTI_NAGRI -> UnicodeScript.SYLOTI_NAGRI
  Character.UnicodeScript.PHAGS_PA -> UnicodeScript.PHAGS_PA
  Character.UnicodeScript.SAURASHTRA -> UnicodeScript.SAURASHTRA
  Character.UnicodeScript.KAYAH_LI -> UnicodeScript.KAYAH_LI
  Character.UnicodeScript.REJANG -> UnicodeScript.REJANG
  Character.UnicodeScript.JAVANESE -> UnicodeScript.JAVANESE
  Character.UnicodeScript.CHAM -> UnicodeScript.CHAM
  Character.UnicodeScript.TAI_VIET -> UnicodeScript.TAI_VIET
  Character.UnicodeScript.MEETEI_MAYEK -> UnicodeScript.MEETEI_MAYEK
  Character.UnicodeScript.LINEAR_B -> UnicodeScript.LINEAR_B
  Character.UnicodeScript.LYCIAN -> UnicodeScript.LYCIAN
  Character.UnicodeScript.CARIAN -> UnicodeScript.CARIAN
  Character.UnicodeScript.OLD_ITALIC -> UnicodeScript.OLD_ITALIC
  Character.UnicodeScript.GOTHIC -> UnicodeScript.GOTHIC
  Character.UnicodeScript.OLD_PERMIC -> UnicodeScript.OLD_PERMIC
  Character.UnicodeScript.UGARITIC -> UnicodeScript.UGARITIC
  Character.UnicodeScript.OLD_PERSIAN -> UnicodeScript.OLD_PERSIAN
  Character.UnicodeScript.DESERET -> UnicodeScript.DESERET
  Character.UnicodeScript.SHAVIAN -> UnicodeScript.SHAVIAN
  Character.UnicodeScript.OSMANYA -> UnicodeScript.OSMANYA
  Character.UnicodeScript.OSAGE -> UnicodeScript.OSAGE
  Character.UnicodeScript.ELBASAN -> UnicodeScript.ELBASAN
  Character.UnicodeScript.CAUCASIAN_ALBANIAN -> UnicodeScript.CAUCASIAN_ALBANIAN
  Character.UnicodeScript.VITHKUQI -> UnicodeScript.VITHKUQI
  Character.UnicodeScript.LINEAR_A -> UnicodeScript.LINEAR_A
  Character.UnicodeScript.CYPRIOT -> UnicodeScript.CYPRIOT
  Character.UnicodeScript.IMPERIAL_ARAMAIC -> UnicodeScript.IMPERIAL_ARAMAIC
  Character.UnicodeScript.PALMYRENE -> UnicodeScript.PALMYRENE
  Character.UnicodeScript.NABATAEAN -> UnicodeScript.NABATAEAN
  Character.UnicodeScript.HATRAN -> UnicodeScript.HATRAN
  Character.UnicodeScript.PHOENICIAN -> UnicodeScript.PHOENICIAN
  Character.UnicodeScript.LYDIAN -> UnicodeScript.LYDIAN
  Character.UnicodeScript.MEROITIC_HIEROGLYPHS -> UnicodeScript.MEROITIC_HIEROGLYPHS
  Character.UnicodeScript.MEROITIC_CURSIVE -> UnicodeScript.MEROITIC_CURSIVE
  Character.UnicodeScript.KHAROSHTHI -> UnicodeScript.KHAROSHTHI
  Character.UnicodeScript.OLD_SOUTH_ARABIAN -> UnicodeScript.OLD_SOUTH_ARABIAN
  Character.UnicodeScript.OLD_NORTH_ARABIAN -> UnicodeScript.OLD_NORTH_ARABIAN
  Character.UnicodeScript.MANICHAEAN -> UnicodeScript.MANICHAEAN
  Character.UnicodeScript.AVESTAN -> UnicodeScript.AVESTAN
  Character.UnicodeScript.INSCRIPTIONAL_PARTHIAN -> UnicodeScript.INSCRIPTIONAL_PARTHIAN
  Character.UnicodeScript.INSCRIPTIONAL_PAHLAVI -> UnicodeScript.INSCRIPTIONAL_PAHLAVI
  Character.UnicodeScript.PSALTER_PAHLAVI -> UnicodeScript.PSALTER_PAHLAVI
  Character.UnicodeScript.OLD_TURKIC -> UnicodeScript.OLD_TURKIC
  Character.UnicodeScript.OLD_HUNGARIAN -> UnicodeScript.OLD_HUNGARIAN
  Character.UnicodeScript.HANIFI_ROHINGYA -> UnicodeScript.HANIFI_ROHINGYA
  Character.UnicodeScript.YEZIDI -> UnicodeScript.YEZIDI
  Character.UnicodeScript.OLD_SOGDIAN -> UnicodeScript.OLD_SOGDIAN
  Character.UnicodeScript.SOGDIAN -> UnicodeScript.SOGDIAN
  Character.UnicodeScript.OLD_UYGHUR -> UnicodeScript.OLD_UYGHUR
  Character.UnicodeScript.CHORASMIAN -> UnicodeScript.CHORASMIAN
  Character.UnicodeScript.ELYMAIC -> UnicodeScript.ELYMAIC
  Character.UnicodeScript.BRAHMI -> UnicodeScript.BRAHMI
  Character.UnicodeScript.KAITHI -> UnicodeScript.KAITHI
  Character.UnicodeScript.SORA_SOMPENG -> UnicodeScript.SORA_SOMPENG
  Character.UnicodeScript.CHAKMA -> UnicodeScript.CHAKMA
  Character.UnicodeScript.MAHAJANI -> UnicodeScript.MAHAJANI
  Character.UnicodeScript.SHARADA -> UnicodeScript.SHARADA
  Character.UnicodeScript.KHOJKI -> UnicodeScript.KHOJKI
  Character.UnicodeScript.MULTANI -> UnicodeScript.MULTANI
  Character.UnicodeScript.KHUDAWADI -> UnicodeScript.KHUDAWADI
  Character.UnicodeScript.GRANTHA -> UnicodeScript.GRANTHA
  Character.UnicodeScript.NEWA -> UnicodeScript.NEWA
  Character.UnicodeScript.TIRHUTA -> UnicodeScript.TIRHUTA
  Character.UnicodeScript.SIDDHAM -> UnicodeScript.SIDDHAM
  Character.UnicodeScript.MODI -> UnicodeScript.MODI
  Character.UnicodeScript.TAKRI -> UnicodeScript.TAKRI
  Character.UnicodeScript.AHOM -> UnicodeScript.AHOM
  Character.UnicodeScript.DOGRA -> UnicodeScript.DOGRA
  Character.UnicodeScript.WARANG_CITI -> UnicodeScript.WARANG_CITI
  Character.UnicodeScript.DIVES_AKURU -> UnicodeScript.DIVES_AKURU
  Character.UnicodeScript.NANDINAGARI -> UnicodeScript.NANDINAGARI
  Character.UnicodeScript.ZANABAZAR_SQUARE -> UnicodeScript.ZANABAZAR_SQUARE
  Character.UnicodeScript.SOYOMBO -> UnicodeScript.SOYOMBO
  Character.UnicodeScript.PAU_CIN_HAU -> UnicodeScript.PAU_CIN_HAU
  Character.UnicodeScript.BHAIKSUKI -> UnicodeScript.BHAIKSUKI
  Character.UnicodeScript.MARCHEN -> UnicodeScript.MARCHEN
  Character.UnicodeScript.MASARAM_GONDI -> UnicodeScript.MASARAM_GONDI
  Character.UnicodeScript.GUNJALA_GONDI -> UnicodeScript.GUNJALA_GONDI
  Character.UnicodeScript.MAKASAR -> UnicodeScript.MAKASAR
  Character.UnicodeScript.KAWI -> UnicodeScript.KAWI
  Character.UnicodeScript.CUNEIFORM -> UnicodeScript.CUNEIFORM
  Character.UnicodeScript.CYPRO_MINOAN -> UnicodeScript.CYPRO_MINOAN
  Character.UnicodeScript.EGYPTIAN_HIEROGLYPHS -> UnicodeScript.EGYPTIAN_HIEROGLYPHS
  Character.UnicodeScript.ANATOLIAN_HIEROGLYPHS -> UnicodeScript.ANATOLIAN_HIEROGLYPHS
  Character.UnicodeScript.MRO -> UnicodeScript.MRO
  Character.UnicodeScript.TANGSA -> UnicodeScript.TANGSA
  Character.UnicodeScript.BASSA_VAH -> UnicodeScript.BASSA_VAH
  Character.UnicodeScript.PAHAWH_HMONG -> UnicodeScript.PAHAWH_HMONG
  Character.UnicodeScript.MEDEFAIDRIN -> UnicodeScript.MEDEFAIDRIN
  Character.UnicodeScript.MIAO -> UnicodeScript.MIAO
  Character.UnicodeScript.TANGUT -> UnicodeScript.TANGUT
  Character.UnicodeScript.NUSHU -> UnicodeScript.NUSHU
  Character.UnicodeScript.KHITAN_SMALL_SCRIPT -> UnicodeScript.KHITAN_SMALL_SCRIPT
  Character.UnicodeScript.DUPLOYAN -> UnicodeScript.DUPLOYAN
  Character.UnicodeScript.SIGNWRITING -> UnicodeScript.SIGNWRITING
  Character.UnicodeScript.NYIAKENG_PUACHUE_HMONG -> UnicodeScript.NYIAKENG_PUACHUE_HMONG
  Character.UnicodeScript.TOTO -> UnicodeScript.TOTO
  Character.UnicodeScript.WANCHO -> UnicodeScript.WANCHO
  Character.UnicodeScript.NAG_MUNDARI -> UnicodeScript.NAG_MUNDARI
  Character.UnicodeScript.MENDE_KIKAKUI -> UnicodeScript.MENDE_KIKAKUI
  Character.UnicodeScript.ADLAM -> UnicodeScript.ADLAM
  else -> {
    try {
      UnicodeScript.valueOf(jvmScript.name)
    }
    catch (_: IllegalArgumentException) {
      UnicodeScript.UNKNOWN
    }
  }
}