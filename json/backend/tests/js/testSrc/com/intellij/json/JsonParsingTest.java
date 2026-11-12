// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.json.syntax.JsonLazyParsingKt;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * @author Mikhail Golubev
 */
@TestApplication
@TestDataPath("$CONTENT_ROOT/testData/psi/")
public class JsonParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  protected final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, PlatformTestUtil.getCommunityPath() + "/json/backend/tests/testData/psi", "json",
                   new JsonParserDefinition());

  private void doTest() {
    assumeFalse(isIgnore(), "Json lazy parsing is disabled");
    ParsingTestFixture fixture = parsingFixture.get();
    String name = fixture.getTestName();
    PsiFile file = fixture.parseFile(name, fixture.loadFile(name + "." + fixture.getFileExt()));
    checkResult(fixture, name, file);
  }

  protected void checkResult(ParsingTestFixture fixture, String targetDataName, PsiFile file) {
    fixture.checkResult(targetDataName, file);
  }

  protected boolean isIgnore() {
    return !JsonLazyParsingKt.getJsonLazyParsing();
  }

  @Test
  public void testKeywords() {
    doTest();
  }

  @Test
  public void testNestedArrayLiterals() {
    doTest();
  }

  @Test
  public void testNestedObjectLiterals() {
    doTest();
  }

  @Test
  public void testTopLevelStringLiteral() {
    doTest();
  }

  @Test
  public void testStringLiterals() {
    doTest();
  }

  @Test
  public void testComments() {
    doTest();
  }

  @Test
  public void testIncompleteObjectProperties() {
    doTest();
  }

  @Test
  public void testMissingCommaBetweenArrayElements() {
    doTest();
  }

  @Test
  public void testMissingCommaBetweenObjectProperties() {
    doTest();
  }

  @Test
  public void testNonStandardPropertyKeys() {
    doTest();
  }

  @Test
  public void testTrailingCommas() {
    doTest();
  }

  // WEB-13600
  @Test
  public void testNumberLiterals() {
    doTest();
  }

  @Test
  public void testExtendedIdentifierToken() {
    doTest();
  }

  // Moved from JavaScript

  @Test
  public void testSimple() {
    doTest();
  }

  @Test
  public void testSimple1() {
    doTest();
  }

  @Test
  public void testSimple2() {
    doTest();
  }

  @Test
  public void testSimple4() {
    doTest();
  }

  @Test
  public void testReal() {
    doTest();
  }

  @Test
  public void testBroken() {
    doTest();
  }

  @Test
  public void testBroken2() {
    doTest();
  }

  @Test
  public void test1000_open_arrays() {
    doTest();
  }

  @Test
  public void test1000_open_objects() {
    doTest();
  }

  @Test
  public void testSimple3() {
    doTest();
  }

  @Test
  public void testReal1() {
    doTest();
  }

  @Test
  public void testReal2() {
    doTest();
  }

  @Test
  public void testn_array_1_true_without_comma() {
    doTest();
  }

  @Test
  public void testn_array_a_invalid_utf8() {
    doTest();
  }

  @Test
  public void testn_array_colon_instead_of_comma() {
    doTest();
  }

  @Test
  public void testn_array_comma_after_close() {
    doTest();
  }

  @Test
  public void testn_array_comma_and_number() {
    doTest();
  }

  @Test
  public void testn_array_double_comma() {
    doTest();
  }

  @Test
  public void testn_array_double_extra_comma() {
    doTest();
  }

  @Test
  public void testn_array_extra_close() {
    doTest();
  }

  @Test
  public void testn_array_extra_comma() {
    doTest();
  }

  @Test
  public void testn_array_incomplete() {
    doTest();
  }

  @Test
  public void testn_array_incomplete_invalid_value() {
    doTest();
  }

  @Test
  public void testn_array_inner_array_no_comma() {
    doTest();
  }

  @Test
  public void testn_array_invalid_utf8() {
    doTest();
  }

  @Test
  public void testn_array_items_separated_by_semicolon() {
    doTest();
  }

  @Test
  public void testn_array_just_comma() {
    doTest();
  }

  @Test
  public void testn_array_just_minus() {
    doTest();
  }

  @Test
  public void testn_array_missing_value() {
    doTest();
  }

  @Test
  public void testn_array_newlines_unclosed() {
    doTest();
  }

  @Test
  public void testn_array_number_and_comma() {
    doTest();
  }

  @Test
  public void testn_array_number_and_several_commas() {
    doTest();
  }

  @Test
  public void testn_array_spaces_vertical_tab_formfeed() {
    doTest();
  }

  @Test
  public void testn_array_star_inside() {
    doTest();
  }

  @Test
  public void testn_array_unclosed() {
    doTest();
  }

  @Test
  public void testn_array_unclosed_trailing_comma() {
    doTest();
  }

  @Test
  public void testn_array_unclosed_with_new_lines() {
    doTest();
  }

  @Test
  public void testn_array_unclosed_with_object_inside() {
    doTest();
  }

  @Test
  public void testn_incomplete_false() {
    doTest();
  }

  @Test
  public void testn_incomplete_null() {
    doTest();
  }

  @Test
  public void testn_incomplete_true() {
    doTest();
  }

  @Test
  public void testn_multidigit_number_then_00() {
    doTest();
  }

  @Test
  public void testn_object_bad_value() {
    doTest();
  }

  @Test
  public void testn_object_bracket_key() {
    doTest();
  }

  @Test
  public void testn_object_comma_instead_of_colon() {
    doTest();
  }

  @Test
  public void testn_object_double_colon() {
    doTest();
  }

  @Test
  public void testn_object_emoji() {
    doTest();
  }

  @Test
  public void testn_object_garbage_at_end() {
    doTest();
  }

  @Test
  public void testn_object_key_with_single_quotes() {
    doTest();
  }

  @Test
  public void testn_object_lone_continuation_byte_in_key_and_trailing_comma() {
    doTest();
  }

  @Test
  public void testn_object_missing_colon() {
    doTest();
  }

  @Test
  public void testn_object_missing_key() {
    doTest();
  }

  @Test
  public void testn_object_missing_semicolon() {
    doTest();
  }

  @Test
  public void testn_object_missing_value() {
    doTest();
  }

  @Test
  public void testn_object_no_colon() {
    doTest();
  }

  @Test
  public void testn_object_non_string_key() {
    doTest();
  }

  @Test
  public void testn_object_non_string_key_but_huge_number_instead() {
    doTest();
  }

  @Test
  public void testn_object_repeated_null_null() {
    doTest();
  }

  @Test
  public void testn_object_several_trailing_commas() {
    doTest();
  }

  @Test
  public void testn_object_single_quote() {
    doTest();
  }

  @Test
  public void testn_object_trailing_comma() {
    doTest();
  }

  @Test
  public void testn_object_trailing_comment() {
    doTest();
  }

  @Test
  public void testn_object_trailing_comment_open() {
    doTest();
  }

  @Test
  public void testn_object_trailing_comment_slash_open() {
    doTest();
  }

  @Test
  public void testn_object_trailing_comment_slash_open_incomplete() {
    doTest();
  }

  @Test
  public void testn_object_two_commas_in_a_row() {
    doTest();
  }

  @Test
  public void testn_object_unquoted_key() {
    doTest();
  }

  @Test
  public void testn_object_unterminated_value() {
    doTest();
  }

  @Test
  public void testn_object_with_single_string() {
    doTest();
  }

  @Test
  public void testn_object_with_trailing_garbage() {
    doTest();
  }

  @Test
  public void testn_single_space() {
    doTest();
  }

  @Test
  public void testn_string_1_surrogate_then_escape() {
    doTest();
  }

  @Test
  public void testn_string_1_surrogate_then_escape_u() {
    doTest();
  }

  @Test
  public void testn_string_1_surrogate_then_escape_u1() {
    doTest();
  }

  @Test
  public void testn_string_1_surrogate_then_escape_u1x() {
    doTest();
  }

  @Test
  public void testn_string_accentuated_char_no_quotes() {
    doTest();
  }

  @Test
  public void testn_string_backslash_00() {
    doTest();
  }

  @Test
  public void testn_string_escape_x() {
    doTest();
  }

  @Test
  public void testn_string_escaped_backslash_bad() {
    doTest();
  }

  @Test
  public void testn_string_escaped_ctrl_char_tab() {
    doTest();
  }

  @Test
  public void testn_string_escaped_emoji() {
    doTest();
  }

  @Test
  public void testn_string_incomplete_escape() {
    doTest();
  }

  @Test
  public void testn_string_incomplete_escaped_character() {
    doTest();
  }

  @Test
  public void testn_string_incomplete_surrogate() {
    doTest();
  }

  @Test
  public void testn_string_incomplete_surrogate_escape_invalid() {
    doTest();
  }

  @Test
  public void testn_string_invalid_utf_8_in_escape() {
    doTest();
  }

  @Test
  public void testn_string_invalid_backslash_esc() {
    doTest();
  }

  @Test
  public void testn_string_invalid_unicode_escape() {
    doTest();
  }

  @Test
  public void testn_string_invalid_utf8_after_escape() {
    doTest();
  }

  @Test
  public void testn_string_leading_uescaped_thinspace() {
    doTest();
  }

  @Test
  public void testn_string_no_quotes_with_bad_escape() {
    doTest();
  }

  @Test
  public void testn_string_single_doublequote() {
    doTest();
  }

  @Test
  public void testn_string_single_quote() {
    doTest();
  }

  @Test
  public void testn_string_single_string_no_double_quotes() {
    doTest();
  }

  @Test
  public void testn_string_start_escape_unclosed() {
    doTest();
  }

  @Test
  public void testn_string_unescaped_crtl_char() {
    doTest();
  }

  @Test
  public void testn_string_unescaped_newline() {
    doTest();
  }

  @Test
  public void testn_string_unescaped_tab() {
    doTest();
  }

  @Test
  public void testn_string_unicode_CapitalU() {
    doTest();
  }

  @Test
  public void testn_string_with_trailing_garbage() {
    doTest();
  }

  @Test
  public void testn_structure_U_2060_word_joined() {
    doTest();
  }

  @Test
  public void testn_structure_UTF8_BOM_no_data() {
    doTest();
  }

  @Test
  public void testn_structure_angle_bracket__() {
    doTest();
  }

  @Test
  public void testn_structure_angle_bracket_null() {
    doTest();
  }

  @Test
  public void testn_structure_array_trailing_garbage() {
    doTest();
  }

  @Test
  public void testn_structure_array_with_extra_array_close() {
    doTest();
  }

  @Test
  public void testn_structure_array_with_unclosed_string() {
    doTest();
  }

  @Test
  public void testn_structure_ascii_unicode_identifier() {
    doTest();
  }

  @Test
  public void testn_structure_capitalized_True() {
    doTest();
  }

  @Test
  public void testn_structure_close_unopened_array() {
    doTest();
  }

  @Test
  public void testn_structure_comma_instead_of_closing_brace() {
    doTest();
  }

  @Test
  public void testn_structure_double_array() {
    doTest();
  }

  @Test
  public void testn_structure_end_array() {
    doTest();
  }

  @Test
  public void testn_structure_incomplete_UTF8_BOM() {
    doTest();
  }

  @Test
  public void testn_structure_lone_invalid_utf_8() {
    doTest();
  }

  @Test
  public void testn_structure_lone_open_bracket() {
    doTest();
  }

  @Test
  public void testn_structure_no_data() {
    doTest();
  }

  @Test
  public void testn_structure_null_byte_outside_string() {
    doTest();
  }

  @Test
  public void testn_structure_number_with_trailing_garbage() {
    doTest();
  }

  @Test
  public void testn_structure_object_followed_by_closing_object() {
    doTest();
  }

  @Test
  public void testn_structure_object_unclosed_no_value() {
    doTest();
  }

  @Test
  public void testn_structure_object_with_comment() {
    doTest();
  }

  @Test
  public void testn_structure_object_with_trailing_garbage() {
    doTest();
  }

  @Test
  public void testn_structure_open_array_apostrophe() {
    doTest();
  }

  @Test
  public void testn_structure_open_array_comma() {
    doTest();
  }

  @Test
  public void testn_structure_open_array_object() {
    doTest();
  }

  @Test
  public void testn_structure_open_array_open_object() {
    doTest();
  }

  @Test
  public void testn_structure_open_array_open_string() {
    doTest();
  }

  @Test
  public void testn_structure_open_array_string() {
    doTest();
  }

  @Test
  public void testn_structure_open_object() {
    doTest();
  }

  @Test
  public void testn_structure_open_object_close_array() {
    doTest();
  }

  @Test
  public void testn_structure_open_object_comma() {
    doTest();
  }

  @Test
  public void testn_structure_open_object_open_array() {
    doTest();
  }

  @Test
  public void testn_structure_open_object_open_string() {
    doTest();
  }

  @Test
  public void testn_structure_open_object_string_with_apostrophes() {
    doTest();
  }

  @Test
  public void testn_structure_open_open() {
    doTest();
  }

  @Test
  public void testn_structure_single_star() {
    doTest();
  }

  @Test
  public void testn_structure_trailing__() {
    doTest();
  }

  @Test
  public void testn_structure_uescaped_LF_before_string() {
    doTest();
  }

  @Test
  public void testn_structure_unclosed_array() {
    doTest();
  }

  @Test
  public void testn_structure_unclosed_array_partial_null() {
    doTest();
  }

  @Test
  public void testn_structure_unclosed_array_unfinished_false() {
    doTest();
  }

  @Test
  public void testn_structure_unclosed_array_unfinished_true() {
    doTest();
  }

  @Test
  public void testn_structure_unclosed_object() {
    doTest();
  }

  @Test
  public void testn_structure_unicode_identifier() {
    doTest();
  }

  @Test
  public void testn_structure_whitespace_U_2060_word_joiner() {
    doTest();
  }

  @Test
  public void testn_structure_whitespace_formfeed() {
    doTest();
  }

  @Test
  public void testy_array_arraysWithSpaces() {
    doTest();
  }

  @Test
  public void testy_array_empty() {
    doTest();
  }

  @Test
  public void testy_array_empty_string() {
    doTest();
  }

  @Test
  public void testy_array_ending_with_newline() {
    doTest();
  }

  @Test
  public void testy_array_false() {
    doTest();
  }

  @Test
  public void testy_array_heterogeneous() {
    doTest();
  }

  @Test
  public void testy_array_null() {
    doTest();
  }

  @Test
  public void testy_array_with_1_and_newline() {
    doTest();
  }

  @Test
  public void testy_array_with_leading_space() {
    doTest();
  }

  @Test
  public void testy_array_with_several_null() {
    doTest();
  }

  @Test
  public void testy_array_with_trailing_space() {
    doTest();
  }

  @Test
  public void testy_number() {
    doTest();
  }

  @Test
  public void testy_number_0e_1() {
    doTest();
  }

  @Test
  public void testy_number_0e1() {
    doTest();
  }

  @Test
  public void testy_number_after_space() {
    doTest();
  }

  @Test
  public void testy_number_double_close_to_zero() {
    doTest();
  }

  @Test
  public void testy_number_int_with_exp() {
    doTest();
  }

  @Test
  public void testy_number_minus_zero() {
    doTest();
  }

  @Test
  public void testy_number_negative_int() {
    doTest();
  }

  @Test
  public void testy_number_negative_one() {
    doTest();
  }

  @Test
  public void testy_number_negative_zero() {
    doTest();
  }

  @Test
  public void testy_number_real_capital_e() {
    doTest();
  }

  @Test
  public void testy_number_real_capital_e_neg_exp() {
    doTest();
  }

  @Test
  public void testy_number_real_capital_e_pos_exp() {
    doTest();
  }

  @Test
  public void testy_number_real_exponent() {
    doTest();
  }

  @Test
  public void testy_number_real_fraction_exponent() {
    doTest();
  }

  @Test
  public void testy_number_real_neg_exp() {
    doTest();
  }

  @Test
  public void testy_number_real_pos_exponent() {
    doTest();
  }

  @Test
  public void testy_number_simple_int() {
    doTest();
  }

  @Test
  public void testy_number_simple_real() {
    doTest();
  }

  @Test
  public void testy_object() {
    doTest();
  }

  @Test
  public void testy_object_basic() {
    doTest();
  }

  @Test
  public void testy_object_duplicated_key() {
    doTest();
  }

  @Test
  public void testy_object_duplicated_key_and_value() {
    doTest();
  }

  @Test
  public void testy_object_empty() {
    doTest();
  }

  @Test
  public void testy_object_empty_key() {
    doTest();
  }

  @Test
  public void testy_object_escaped_null_in_key() {
    doTest();
  }

  @Test
  public void testy_object_extreme_numbers() {
    doTest();
  }

  @Test
  public void testy_object_long_strings() {
    doTest();
  }

  @Test
  public void testy_object_simple() {
    doTest();
  }

  @Test
  public void testy_object_string_unicode() {
    doTest();
  }

  @Test
  public void testy_object_with_newlines() {
    doTest();
  }

  @Test
  public void testy_string_1_2_3_bytes_UTF_8_sequences() {
    doTest();
  }

  @Test
  public void testy_string_accepted_surrogate_pair() {
    doTest();
  }

  @Test
  public void testy_string_accepted_surrogate_pairs() {
    doTest();
  }

  @Test
  public void testy_string_allowed_escapes() {
    doTest();
  }

  @Test
  public void testy_string_backslash_and_u_escaped_zero() {
    doTest();
  }

  @Test
  public void testy_string_backslash_doublequotes() {
    doTest();
  }

  @Test
  public void testy_string_comments() {
    doTest();
  }

  @Test
  public void testy_string_double_escape_a() {
    doTest();
  }

  @Test
  public void testy_string_double_escape_n() {
    doTest();
  }

  @Test
  public void testy_string_escaped_control_character() {
    doTest();
  }

  @Test
  public void testy_string_escaped_noncharacter() {
    doTest();
  }

  @Test
  public void testy_string_in_array() {
    doTest();
  }

  @Test
  public void testy_string_in_array_with_leading_space() {
    doTest();
  }

  @Test
  public void testy_string_last_surrogates_1_and_2() {
    doTest();
  }

  @Test
  public void testy_string_nbsp_uescaped() {
    doTest();
  }

  @Test
  public void testy_string_nonCharacterInUTF_8_U_10FFFF() {
    doTest();
  }

  @Test
  public void testy_string_nonCharacterInUTF_8_U_FFFF() {
    doTest();
  }

  @Test
  public void testy_string_null_escape() {
    doTest();
  }

  @Test
  public void testy_string_one_byte_utf_8() {
    doTest();
  }

  @Test
  public void testy_string_pi() {
    doTest();
  }

  @Test
  public void testy_string_reservedCharacterInUTF_8_U_1BFFF() {
    doTest();
  }

  @Test
  public void testy_string_simple_ascii() {
    doTest();
  }

  @Test
  public void testy_string_space() {
    doTest();
  }

  @Test
  public void testy_string_surrogates_U_1D11E_MUSICAL_SYMBOL_G_CLEF() {
    doTest();
  }

  @Test
  public void testy_string_three_byte_utf_8() {
    doTest();
  }

  @Test
  public void testy_string_two_byte_utf_8() {
    doTest();
  }

  @Test
  public void testy_string_u_2028_line_sep() {
    doTest();
  }

  @Test
  public void testy_string_u_2029_par_sep() {
    doTest();
  }

  @Test
  public void testy_string_uEscape() {
    doTest();
  }

  @Test
  public void testy_string_uescaped_newline() {
    doTest();
  }

  @Test
  public void testy_string_unescaped_char_delete() {
    doTest();
  }

  @Test
  public void testy_string_unicode() {
    doTest();
  }

  @Test
  public void testy_string_unicodeEscapedBackslash() {
    doTest();
  }

  @Test
  public void testy_string_unicode_2() {
    doTest();
  }

  @Test
  public void testy_string_unicode_U_10FFFE_nonchar() {
    doTest();
  }

  @Test
  public void testy_string_unicode_U_1FFFE_nonchar() {
    doTest();
  }

  @Test
  public void testy_string_unicode_U_200B_ZERO_WIDTH_SPACE() {
    doTest();
  }

  @Test
  public void testy_string_unicode_U_2064_invisible_plus() {
    doTest();
  }

  @Test
  public void testy_string_unicode_U_FDD0_nonchar() {
    doTest();
  }

  @Test
  public void testy_string_unicode_U_FFFE_nonchar() {
    doTest();
  }

  @Test
  public void testy_string_unicode_escaped_double_quote() {
    doTest();
  }

  @Test
  public void testy_string_utf8() {
    doTest();
  }

  @Test
  public void testy_string_with_del_character() {
    doTest();
  }

  @Test
  public void testy_structure_lonely_false() {
    doTest();
  }

  @Test
  public void testy_structure_lonely_int() {
    doTest();
  }

  @Test
  public void testy_structure_lonely_negative_real() {
    doTest();
  }

  @Test
  public void testy_structure_lonely_null() {
    doTest();
  }

  @Test
  public void testy_structure_lonely_string() {
    doTest();
  }

  @Test
  public void testy_structure_lonely_true() {
    doTest();
  }

  @Test
  public void testy_structure_string_empty() {
    doTest();
  }

  @Test
  public void testy_structure_trailing_newline() {
    doTest();
  }

  @Test
  public void testy_structure_true_in_array() {
    doTest();
  }

  @Test
  public void testy_structure_whitespace_array() {
    doTest();
  }
}
