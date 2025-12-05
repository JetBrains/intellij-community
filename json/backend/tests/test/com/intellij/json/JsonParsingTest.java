// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.json.psi.JsonElementTypeConverterFactory;
import com.intellij.json.syntax.JsonLanguageDefinition;
import com.intellij.platform.syntax.psi.CommonElementTypeConverterFactory;
import com.intellij.platform.syntax.psi.ElementTypeConverters;
import com.intellij.platform.syntax.psi.LanguageSyntaxDefinitions;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;

/**
 * @author Mikhail Golubev
 */
@TestDataPath("$CONTENT_ROOT/testData/psi/")
public class JsonParsingTest extends ParsingTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addExplicitExtension(ElementTypeConverters.getInstance(), JsonLanguage.INSTANCE, new JsonElementTypeConverterFactory());
    addExplicitExtension(ElementTypeConverters.getInstance(), JsonLanguage.INSTANCE, new CommonElementTypeConverterFactory());
    addExplicitExtension(ElementTypeConverters.getInstance(), JsonLanguage.INSTANCE, new JsonFileTypeConverterFactory());
    addExplicitExtension(LanguageSyntaxDefinitions.getINSTANCE(), JsonLanguage.INSTANCE, new JsonLanguageDefinition());
  }

  public JsonParsingTest() {
    super("psi", "json", new JsonParserDefinition());
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/backend/tests/testData";
  }

  private void doTest() {
    doTest(true);
  }

  public void testKeywords() {
    doTest();
  }

  public void testNestedArrayLiterals() {
    doTest();
  }

  public void testNestedObjectLiterals() {
    doTest();
  }

  public void testTopLevelStringLiteral() {
    doTest();
  }

  public void testStringLiterals() {
    doTest();
  }

  public void testComments() {
    doTest();
  }

  public void testIncompleteObjectProperties() {
    doTest();
  }

  public void testMissingCommaBetweenArrayElements() {
    doTest();
  }

  public void testMissingCommaBetweenObjectProperties() {
    doTest();
  }

  public void testNonStandardPropertyKeys() {
    doTest();
  }

  public void testTrailingCommas() {
    doTest();
  }

  // WEB-13600
  public void testNumberLiterals() {
    doTest();
  }

  public void testExtendedIdentifierToken() {
    doTest();
  }

  // Moved from JavaScript

  public void testSimple() {
    doTest();
  }

  public void testSimple1() {
    doTest();
  }

  public void testSimple2() {
    doTest();
  }

  public void testSimple4() {
    doTest();
  }

  public void testReal() {
    doTest();
  }

  public void test1000_open_arrays() {
    doTest();
  }

  public void test1000_open_objects() {
    doTest();
  }

  public void test100_100() {
    doTest();
  }

  public void testSimple3() {
    doTest();
  }

  public void testReal1() {
    doTest();
  }

  public void testReal2() {
    doTest();
  }

  public void testn_array_1_true_without_comma() {
    doTest();
  }

  public void testn_array_a_invalid_utf8() {
    doTest();
  }

  public void testn_array_colon_instead_of_comma() {
    doTest();
  }

  public void testn_array_comma_after_close() {
    doTest();
  }

  public void testn_array_comma_and_number() {
    doTest();
  }

  public void testn_array_double_comma() {
    doTest();
  }

  public void testn_array_double_extra_comma() {
    doTest();
  }

  public void testn_array_extra_close() {
    doTest();
  }

  public void testn_array_extra_comma() {
    doTest();
  }

  public void testn_array_incomplete() {
    doTest();
  }

  public void testn_array_incomplete_invalid_value() {
    doTest();
  }

  public void testn_array_inner_array_no_comma() {
    doTest();
  }

  public void testn_array_invalid_utf8() {
    doTest();
  }

  public void testn_array_items_separated_by_semicolon() {
    doTest();
  }

  public void testn_array_just_comma() {
    doTest();
  }

  public void testn_array_just_minus() {
    doTest();
  }

  public void testn_array_missing_value() {
    doTest();
  }

  public void testn_array_newlines_unclosed() {
    doTest();
  }

  public void testn_array_number_and_comma() {
    doTest();
  }

  public void testn_array_number_and_several_commas() {
    doTest();
  }

  public void testn_array_spaces_vertical_tab_formfeed() {
    doTest();
  }

  public void testn_array_star_inside() {
    doTest();
  }

  public void testn_array_unclosed() {
    doTest();
  }

  public void testn_array_unclosed_trailing_comma() {
    doTest();
  }

  public void testn_array_unclosed_with_new_lines() {
    doTest();
  }

  public void testn_array_unclosed_with_object_inside() {
    doTest();
  }

  public void testn_incomplete_false() {
    doTest();
  }

  public void testn_incomplete_null() {
    doTest();
  }

  public void testn_incomplete_true() {
    doTest();
  }

  public void testn_multidigit_number_then_00() {
    doTest();
  }

  public void testn_object_bad_value() {
    doTest();
  }

  public void testn_object_bracket_key() {
    doTest();
  }

  public void testn_object_comma_instead_of_colon() {
    doTest();
  }

  public void testn_object_double_colon() {
    doTest();
  }

  public void testn_object_emoji() {
    doTest();
  }

  public void testn_object_garbage_at_end() {
    doTest();
  }

  public void testn_object_key_with_single_quotes() {
    doTest();
  }

  public void testn_object_lone_continuation_byte_in_key_and_trailing_comma() {
    doTest();
  }

  public void testn_object_missing_colon() {
    doTest();
  }

  public void testn_object_missing_key() {
    doTest();
  }

  public void testn_object_missing_semicolon() {
    doTest();
  }

  public void testn_object_missing_value() {
    doTest();
  }

  public void testn_object_no_colon() {
    doTest();
  }

  public void testn_object_non_string_key() {
    doTest();
  }

  public void testn_object_non_string_key_but_huge_number_instead() {
    doTest();
  }

  public void testn_object_repeated_null_null() {
    doTest();
  }

  public void testn_object_several_trailing_commas() {
    doTest();
  }

  public void testn_object_single_quote() {
    doTest();
  }

  public void testn_object_trailing_comma() {
    doTest();
  }

  public void testn_object_trailing_comment() {
    doTest();
  }

  public void testn_object_trailing_comment_open() {
    doTest();
  }

  public void testn_object_trailing_comment_slash_open() {
    doTest();
  }

  public void testn_object_trailing_comment_slash_open_incomplete() {
    doTest();
  }

  public void testn_object_two_commas_in_a_row() {
    doTest();
  }

  public void testn_object_unquoted_key() {
    doTest();
  }

  public void testn_object_unterminated_value() {
    doTest();
  }

  public void testn_object_with_single_string() {
    doTest();
  }

  public void testn_object_with_trailing_garbage() {
    doTest();
  }

  public void testn_single_space() {
    doTest();
  }

  public void testn_string_1_surrogate_then_escape() {
    doTest();
  }

  public void testn_string_1_surrogate_then_escape_u() {
    doTest();
  }

  public void testn_string_1_surrogate_then_escape_u1() {
    doTest();
  }

  public void testn_string_1_surrogate_then_escape_u1x() {
    doTest();
  }

  public void testn_string_accentuated_char_no_quotes() {
    doTest();
  }

  public void testn_string_backslash_00() {
    doTest();
  }

  public void testn_string_escape_x() {
    doTest();
  }

  public void testn_string_escaped_backslash_bad() {
    doTest();
  }

  public void testn_string_escaped_ctrl_char_tab() {
    doTest();
  }

  public void testn_string_escaped_emoji() {
    doTest();
  }

  public void testn_string_incomplete_escape() {
    doTest();
  }

  public void testn_string_incomplete_escaped_character() {
    doTest();
  }

  public void testn_string_incomplete_surrogate() {
    doTest();
  }

  public void testn_string_incomplete_surrogate_escape_invalid() {
    doTest();
  }

  public void testn_string_invalid_utf_8_in_escape() {
    doTest();
  }

  public void testn_string_invalid_backslash_esc() {
    doTest();
  }

  public void testn_string_invalid_unicode_escape() {
    doTest();
  }

  public void testn_string_invalid_utf8_after_escape() {
    doTest();
  }

  public void testn_string_leading_uescaped_thinspace() {
    doTest();
  }

  public void testn_string_no_quotes_with_bad_escape() {
    doTest();
  }

  public void testn_string_single_doublequote() {
    doTest();
  }

  public void testn_string_single_quote() {
    doTest();
  }

  public void testn_string_single_string_no_double_quotes() {
    doTest();
  }

  public void testn_string_start_escape_unclosed() {
    doTest();
  }

  public void testn_string_unescaped_crtl_char() {
    doTest();
  }

  public void testn_string_unescaped_newline() {
    doTest();
  }

  public void testn_string_unescaped_tab() {
    doTest();
  }

  public void testn_string_unicode_CapitalU() {
    doTest();
  }

  public void testn_string_with_trailing_garbage() {
    doTest();
  }

  public void testn_structure_U_2060_word_joined() {
    doTest();
  }

  public void testn_structure_UTF8_BOM_no_data() {
    doTest();
  }

  public void testn_structure_angle_bracket__() {
    doTest();
  }

  public void testn_structure_angle_bracket_null() {
    doTest();
  }

  public void testn_structure_array_trailing_garbage() {
    doTest();
  }

  public void testn_structure_array_with_extra_array_close() {
    doTest();
  }

  public void testn_structure_array_with_unclosed_string() {
    doTest();
  }

  public void testn_structure_ascii_unicode_identifier() {
    doTest();
  }

  public void testn_structure_capitalized_True() {
    doTest();
  }

  public void testn_structure_close_unopened_array() {
    doTest();
  }

  public void testn_structure_comma_instead_of_closing_brace() {
    doTest();
  }

  public void testn_structure_double_array() {
    doTest();
  }

  public void testn_structure_end_array() {
    doTest();
  }

  public void testn_structure_incomplete_UTF8_BOM() {
    doTest();
  }

  public void testn_structure_lone_invalid_utf_8() {
    doTest();
  }

  public void testn_structure_lone_open_bracket() {
    doTest();
  }

  public void testn_structure_no_data() {
    doTest();
  }

  public void testn_structure_null_byte_outside_string() {
    doTest();
  }

  public void testn_structure_number_with_trailing_garbage() {
    doTest();
  }

  public void testn_structure_object_followed_by_closing_object() {
    doTest();
  }

  public void testn_structure_object_unclosed_no_value() {
    doTest();
  }

  public void testn_structure_object_with_comment() {
    doTest();
  }

  public void testn_structure_object_with_trailing_garbage() {
    doTest();
  }

  public void testn_structure_open_array_apostrophe() {
    doTest();
  }

  public void testn_structure_open_array_comma() {
    doTest();
  }

  public void testn_structure_open_array_object() {
    doTest();
  }

  public void testn_structure_open_array_open_object() {
    doTest();
  }

  public void testn_structure_open_array_open_string() {
    doTest();
  }

  public void testn_structure_open_array_string() {
    doTest();
  }

  public void testn_structure_open_object() {
    doTest();
  }

  public void testn_structure_open_object_close_array() {
    doTest();
  }

  public void testn_structure_open_object_comma() {
    doTest();
  }

  public void testn_structure_open_object_open_array() {
    doTest();
  }

  public void testn_structure_open_object_open_string() {
    doTest();
  }

  public void testn_structure_open_object_string_with_apostrophes() {
    doTest();
  }

  public void testn_structure_open_open() {
    doTest();
  }

  public void testn_structure_single_star() {
    doTest();
  }

  public void testn_structure_trailing__() {
    doTest();
  }

  public void testn_structure_uescaped_LF_before_string() {
    doTest();
  }

  public void testn_structure_unclosed_array() {
    doTest();
  }

  public void testn_structure_unclosed_array_partial_null() {
    doTest();
  }

  public void testn_structure_unclosed_array_unfinished_false() {
    doTest();
  }

  public void testn_structure_unclosed_array_unfinished_true() {
    doTest();
  }

  public void testn_structure_unclosed_object() {
    doTest();
  }

  public void testn_structure_unicode_identifier() {
    doTest();
  }

  public void testn_structure_whitespace_U_2060_word_joiner() {
    doTest();
  }

  public void testn_structure_whitespace_formfeed() {
    doTest();
  }

  public void testy_array_arraysWithSpaces() {
    doTest();
  }

  public void testy_array_empty() {
    doTest();
  }

  public void testy_array_empty_string() {
    doTest();
  }

  public void testy_array_ending_with_newline() {
    doTest();
  }

  public void testy_array_false() {
    doTest();
  }

  public void testy_array_heterogeneous() {
    doTest();
  }

  public void testy_array_null() {
    doTest();
  }

  public void testy_array_with_1_and_newline() {
    doTest();
  }

  public void testy_array_with_leading_space() {
    doTest();
  }

  public void testy_array_with_several_null() {
    doTest();
  }

  public void testy_array_with_trailing_space() {
    doTest();
  }

  public void testy_number() {
    doTest();
  }

  public void testy_number_0e_1() {
    doTest();
  }

  public void testy_number_0e1() {
    doTest();
  }

  public void testy_number_after_space() {
    doTest();
  }

  public void testy_number_double_close_to_zero() {
    doTest();
  }

  public void testy_number_int_with_exp() {
    doTest();
  }

  public void testy_number_minus_zero() {
    doTest();
  }

  public void testy_number_negative_int() {
    doTest();
  }

  public void testy_number_negative_one() {
    doTest();
  }

  public void testy_number_negative_zero() {
    doTest();
  }

  public void testy_number_real_capital_e() {
    doTest();
  }

  public void testy_number_real_capital_e_neg_exp() {
    doTest();
  }

  public void testy_number_real_capital_e_pos_exp() {
    doTest();
  }

  public void testy_number_real_exponent() {
    doTest();
  }

  public void testy_number_real_fraction_exponent() {
    doTest();
  }

  public void testy_number_real_neg_exp() {
    doTest();
  }

  public void testy_number_real_pos_exponent() {
    doTest();
  }

  public void testy_number_simple_int() {
    doTest();
  }

  public void testy_number_simple_real() {
    doTest();
  }

  public void testy_object() {
    doTest();
  }

  public void testy_object_basic() {
    doTest();
  }

  public void testy_object_duplicated_key() {
    doTest();
  }

  public void testy_object_duplicated_key_and_value() {
    doTest();
  }

  public void testy_object_empty() {
    doTest();
  }

  public void testy_object_empty_key() {
    doTest();
  }

  public void testy_object_escaped_null_in_key() {
    doTest();
  }

  public void testy_object_extreme_numbers() {
    doTest();
  }

  public void testy_object_long_strings() {
    doTest();
  }

  public void testy_object_simple() {
    doTest();
  }

  public void testy_object_string_unicode() {
    doTest();
  }

  public void testy_object_with_newlines() {
    doTest();
  }

  public void testy_string_1_2_3_bytes_UTF_8_sequences() {
    doTest();
  }

  public void testy_string_accepted_surrogate_pair() {
    doTest();
  }

  public void testy_string_accepted_surrogate_pairs() {
    doTest();
  }

  public void testy_string_allowed_escapes() {
    doTest();
  }

  public void testy_string_backslash_and_u_escaped_zero() {
    doTest();
  }

  public void testy_string_backslash_doublequotes() {
    doTest();
  }

  public void testy_string_comments() {
    doTest();
  }

  public void testy_string_double_escape_a() {
    doTest();
  }

  public void testy_string_double_escape_n() {
    doTest();
  }

  public void testy_string_escaped_control_character() {
    doTest();
  }

  public void testy_string_escaped_noncharacter() {
    doTest();
  }

  public void testy_string_in_array() {
    doTest();
  }

  public void testy_string_in_array_with_leading_space() {
    doTest();
  }

  public void testy_string_last_surrogates_1_and_2() {
    doTest();
  }

  public void testy_string_nbsp_uescaped() {
    doTest();
  }

  public void testy_string_nonCharacterInUTF_8_U_10FFFF() {
    doTest();
  }

  public void testy_string_nonCharacterInUTF_8_U_FFFF() {
    doTest();
  }

  public void testy_string_null_escape() {
    doTest();
  }

  public void testy_string_one_byte_utf_8() {
    doTest();
  }

  public void testy_string_pi() {
    doTest();
  }

  public void testy_string_reservedCharacterInUTF_8_U_1BFFF() {
    doTest();
  }

  public void testy_string_simple_ascii() {
    doTest();
  }

  public void testy_string_space() {
    doTest();
  }

  public void testy_string_surrogates_U_1D11E_MUSICAL_SYMBOL_G_CLEF() {
    doTest();
  }

  public void testy_string_three_byte_utf_8() {
    doTest();
  }

  public void testy_string_two_byte_utf_8() {
    doTest();
  }

  public void testy_string_u_2028_line_sep() {
    doTest();
  }

  public void testy_string_u_2029_par_sep() {
    doTest();
  }

  public void testy_string_uEscape() {
    doTest();
  }

  public void testy_string_uescaped_newline() {
    doTest();
  }

  public void testy_string_unescaped_char_delete() {
    doTest();
  }

  public void testy_string_unicode() {
    doTest();
  }

  public void testy_string_unicodeEscapedBackslash() {
    doTest();
  }

  public void testy_string_unicode_2() {
    doTest();
  }

  public void testy_string_unicode_U_10FFFE_nonchar() {
    doTest();
  }

  public void testy_string_unicode_U_1FFFE_nonchar() {
    doTest();
  }

  public void testy_string_unicode_U_200B_ZERO_WIDTH_SPACE() {
    doTest();
  }

  public void testy_string_unicode_U_2064_invisible_plus() {
    doTest();
  }

  public void testy_string_unicode_U_FDD0_nonchar() {
    doTest();
  }

  public void testy_string_unicode_U_FFFE_nonchar() {
    doTest();
  }

  public void testy_string_unicode_escaped_double_quote() {
    doTest();
  }

  public void testy_string_utf8() {
    doTest();
  }

  public void testy_string_with_del_character() {
    doTest();
  }

  public void testy_structure_lonely_false() {
    doTest();
  }

  public void testy_structure_lonely_int() {
    doTest();
  }

  public void testy_structure_lonely_negative_real() {
    doTest();
  }

  public void testy_structure_lonely_null() {
    doTest();
  }

  public void testy_structure_lonely_string() {
    doTest();
  }

  public void testy_structure_lonely_true() {
    doTest();
  }

  public void testy_structure_string_empty() {
    doTest();
  }

  public void testy_structure_trailing_newline() {
    doTest();
  }

  public void testy_structure_true_in_array() {
    doTest();
  }

  public void testy_structure_whitespace_array() {
    doTest();
  }
}
