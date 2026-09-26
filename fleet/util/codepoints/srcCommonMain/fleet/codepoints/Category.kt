package fleet.codepoints

/**
 * Unicode general category of a code point, as defined by the Unicode standard.
 *
 * [code] is the two-letter category abbreviation used in the Unicode Character Database
 * (e.g. `Lu` for an uppercase letter).
 *
 * The declaration order is significant: ordinals match the category codes packed into
 * the generated `CharacterData` property tables (see `GeneralCategory` in the generator).
 */
enum class Category(val code: String) {
    UNASSIGNED("Cn"),
    UPPERCASE_LETTER("Lu"),
    LOWERCASE_LETTER("Ll"),
    TITLECASE_LETTER("Lt"),
    MODIFIER_LETTER("Lm"),
    OTHER_LETTER("Lo"),
    NON_SPACING_MARK("Mn"),
    COMBINING_SPACING_MARK("Mc"),
    ENCLOSING_MARK("Me"),
    DECIMAL_DIGIT_NUMBER("Nd"),
    LETTER_NUMBER("Nl"),
    OTHER_NUMBER("No"),
    CONNECTOR_PUNCTUATION("Pc"),
    DASH_PUNCTUATION("Pd"),
    START_PUNCTUATION("Ps"),
    END_PUNCTUATION("Pe"),
    INITIAL_QUOTE_PUNCTUATION("Pi"),
    FINAL_QUOTE_PUNCTUATION("Pf"),
    OTHER_PUNCTUATION("Po"),
    MATH_SYMBOL("Sm"),
    CURRENCY_SYMBOL("Sc"),
    MODIFIER_SYMBOL("Sk"),
    OTHER_SYMBOL("So"),
    SPACE_SEPARATOR("Zs"),
    LINE_SEPARATOR("Zl"),
    PARAGRAPH_SEPARATOR("Zp"),
    CONTROL("Cc"),
    FORMAT("Cf"),
    SURROGATE("Cs"),
    PRIVATE_USE("Co");
}
