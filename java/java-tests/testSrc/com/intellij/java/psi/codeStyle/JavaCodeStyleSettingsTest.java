/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi.codeStyle;

import com.intellij.application.options.codeStyle.properties.CodeStylePropertyMapper;
import com.intellij.ide.codeStyleSettings.CodeStyleTestCase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.psi.codeStyle.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

public class JavaCodeStyleSettingsTest extends CodeStyleTestCase {

  public void testSettingsClone() {
    JavaCodeStyleSettings original = JavaCodeStyleSettings.getInstance(getProject());
    original.getImportLayoutTable().addEntry(new PackageEntry(false, "test", true));
    List<String> annotations = Arrays.asList("anno1", "anno2");
    original.setRepeatAnnotations(annotations);
    original.getPackagesToUseImportOnDemand().addEntry(new PackageEntry(false, "test2", true));
    original.FIELD_TYPE_TO_NAME.addPair("foo", "bar");
    original.STATIC_FIELD_TYPE_TO_NAME.addPair("one", "two");

    JavaCodeStyleSettings copy = (JavaCodeStyleSettings)original.clone();
    assertEquals(annotations, copy.getRepeatAnnotations());
    assertEquals("Import tables do not match", original.getImportLayoutTable(), copy.getImportLayoutTable());
    assertEquals("On demand packages do not match", original.getPackagesToUseImportOnDemand(), copy.getPackagesToUseImportOnDemand());
    assertEquals("Field type-to-name maps don not match", original.FIELD_TYPE_TO_NAME, copy.FIELD_TYPE_TO_NAME);
    assertEquals("Static field type-to-name maps don not match", original.STATIC_FIELD_TYPE_TO_NAME, copy.STATIC_FIELD_TYPE_TO_NAME);

    copy.setRepeatAnnotations(Arrays.asList("anno1"));
    assertNotSame("Changed repeated annotations should reflect the equality relation", original, copy);
  }

  public void testSettingsCloneNotReferencingOriginal() throws IllegalAccessException {
    JavaCodeStyleSettings original = JavaCodeStyleSettings.getInstance(getProject());
    JavaCodeStyleSettings copy = (JavaCodeStyleSettings)original.clone();
    for (Field field : copy.getClass().getDeclaredFields()) {
      if (!isPrimitiveOrString(field.getType()) && (field.getModifiers() & Modifier.PUBLIC) != 0) {
        assertNotSame("Fields '" + field.getName() + "' reference the same value", field.get(original), field.get(copy));
      }
    }
  }

  public void testImportPre173Settings() throws SchemeImportException {
    CodeStyleSettings imported = importSettings();
    CommonCodeStyleSettings commonSettings = imported.getCommonSettings(JavaLanguage.INSTANCE);
    assertEquals("testprefix", imported.getCustomSettings(JavaCodeStyleSettings.class).FIELD_NAME_PREFIX);
    assertTrue(commonSettings.WRAP_COMMENTS);
    assertFalse(imported.WRAP_COMMENTS);
  }

  public void testGetProperties() {
    final CodeStyleSettings settings = getCurrentCodeStyleSettings();
    final CommonCodeStyleSettings commonJavaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    commonJavaSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    commonJavaSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    final JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    javaSettings.FIELD_NAME_PREFIX = "m_";
    javaSettings.STATIC_FIELD_NAME_SUFFIX = "_s";
    CodeStylePropertyMapper mapper =
      LanguageCodeStyleSettingsProvider.forLanguage(JavaLanguage.INSTANCE).getPropertyMapper(settings);
    StringBuilder builder = new StringBuilder();
    for (String property : mapper.enumProperties()) {
      String value = mapper.getProperty(property);
      if (value != null) {
        builder.append(property).append(" = ").append(value).append('\n');
      }
    }
    assertEquals(
      "align_consecutive_assignments = false\n" +
      "align_consecutive_variable_declarations = false\n" +
      "align_group_field_declarations = false\n" +
      "align_multiline_annotation_parameters = false\n" +
      "align_multiline_array_initializer_expression = false\n" +
      "align_multiline_assignment = false\n" +
      "align_multiline_binary_operation = false\n" +
      "align_multiline_chained_methods = false\n" +
      "align_multiline_extends_list = false\n" +
      "align_multiline_for = true\n" +
      "align_multiline_method_brackets = false\n" +
      "align_multiline_parameters = true\n" +
      "align_multiline_parameters_in_calls = false\n" +
      "align_multiline_parenthesized_expression = false\n" +
      "align_multiline_resources = true\n" +
      "align_multiline_ternary_operation = false\n" +
      "align_multiline_throws_list = false\n" +
      "align_subsequent_simple_methods = false\n" +
      "align_throws_keyword = false\n" +
      "annotation_parameter_wrap = never\n" +
      "array_initializer_left_brace_on_next_line = false\n" +
      "array_initializer_right_brace_on_next_line = false\n" +
      "array_initializer_wrap = never\n" +
      "assert_statement_colon_on_next_line = false\n" +
      "assert_statement_wrap = never\n" +
      "assignment_wrap = never\n" +
      "binary_operation_sign_on_next_line = false\n" +
      "binary_operation_wrap = never\n" +
      "blank_lines_after_anonymous_class_header = 0\n" +
      "blank_lines_after_class_header = 0\n" +
      "blank_lines_after_imports = 1\n" +
      "blank_lines_after_package = 1\n" +
      "blank_lines_around_class = 1\n" +
      "blank_lines_around_field = 0\n" +
      "blank_lines_around_field_in_interface = 0\n" +
      "blank_lines_around_initializer = 1\n" +
      "blank_lines_around_method = 1\n" +
      "blank_lines_around_method_in_interface = 1\n" +
      "blank_lines_before_class_end = 0\n" +
      "blank_lines_before_imports = 1\n" +
      "blank_lines_before_method_body = 0\n" +
      "blank_lines_before_package = 0\n" +
      "block_comment_at_first_column = true\n" +
      "brace_style = end_of_line\n" +
      "call_parameters_left_paren_on_next_line = false\n" +
      "call_parameters_right_paren_on_next_line = false\n" +
      "call_parameters_wrap = on_every_item\n" +
      "case_statement_on_new_line = true\n" +
      "catch_on_new_line = false\n" +
      "class_annotation_wrap = always\n" +
      "class_brace_style = end_of_line\n" +
      "class_count_to_use_import_on_demand = 5\n" +
      "class_names_in_javadoc = 1\n" +
      "continuation_indent_size = 8\n" +
      "do_not_indent_top_level_class_members = false\n" +
      "do_not_wrap_after_single_annotation = false\n" +
      "do_while_brace_force = never\n" +
      "else_on_new_line = false\n" +
      "enable_javadoc_formatting = true\n" +
      "enum_constants_wrap = never\n" +
      "extends_keyword_wrap = never\n" +
      "extends_list_wrap = never\n" +
      "field_annotation_wrap = always\n" +
      "finally_on_new_line = false\n" +
      "for_brace_force = never\n" +
      "for_statement_left_paren_on_next_line = false\n" +
      "for_statement_right_paren_on_next_line = false\n" +
      "for_statement_wrap = never\n" +
      "generate_final_locals = false\n" +
      "generate_final_parameters = false\n" +
      "if_brace_force = never\n" +
      "indent_case_from_switch = true\n" +
      "indent_size = 4\n" +
      "insert_inner_class_imports = false\n" +
      "insert_override_annotation = true\n" +
      "javadoc_add_blank_after_description = true\n" +
      "javadoc_add_blank_after_param_comments = false\n" +
      "javadoc_add_blank_after_return = false\n" +
      "javadoc_align_exception_comments = true\n" +
      "javadoc_align_param_comments = true\n" +
      "javadoc_do_not_wrap_one_line_comments = false\n" +
      "javadoc_indent_on_continuation = false\n" +
      "javadoc_keep_empty_exception = true\n" +
      "javadoc_keep_empty_lines = true\n" +
      "javadoc_keep_empty_parameter = true\n" +
      "javadoc_keep_empty_return = true\n" +
      "javadoc_keep_invalid_tags = true\n" +
      "javadoc_leading_asterisks_are_enabled = true\n" +
      "javadoc_p_at_empty_lines = true\n" +
      "javadoc_param_description_on_new_line = false\n" +
      "javadoc_preserve_line_feeds = false\n" +
      "javadoc_use_throws_not_exception = true\n" +
      "keep_blank_lines_before_right_brace = 2\n" +
      "keep_blank_lines_between_package_declaration_and_header = 2\n" +
      "keep_blank_lines_in_code = 2\n" +
      "keep_blank_lines_in_declarations = 2\n" +
      "keep_control_statement_in_one_line = true\n" +
      "keep_first_column_comment = true\n" +
      "keep_indents_on_empty_lines = false\n" +
      "keep_line_breaks = true\n" +
      "keep_multiple_expressions_in_one_line = false\n" +
      "keep_simple_blocks_in_one_line = false\n" +
      "keep_simple_classes_in_one_line = false\n" +
      "keep_simple_lambdas_in_one_line = false\n" +
      "keep_simple_methods_in_one_line = false\n" +
      "lambda_brace_style = end_of_line\n" +
      "layout_static_imports_separately = true\n" +
      "line_comment_add_space = false\n" +
      "line_comment_at_first_column = true\n" +
      "method_annotation_wrap = always\n" +
      "method_brace_style = end_of_line\n" +
      "method_call_chain_wrap = never\n" +
      "method_parameters_left_paren_on_next_line = false\n" +
      "method_parameters_right_paren_on_next_line = false\n" +
      "method_parameters_wrap = if_long\n" +
      "modifier_list_wrap = false\n" +
      "names_count_to_use_import_on_demand = 3\n" +
      "parameter_annotation_wrap = never\n" +
      "parentheses_expression_left_paren_wrap = false\n" +
      "parentheses_expression_right_paren_wrap = false\n" +
      "place_assignment_sign_on_next_line = false\n" +
      "prefer_longer_names = true\n" +
      "prefer_parameters_wrap = false\n" +
      "repeat_synchronized = true\n" +
      "replace_instance_of = false\n" +
      "replace_null_check = true\n" +
      "resource_list_left_paren_on_next_line = false\n" +
      "resource_list_right_paren_on_next_line = false\n" +
      "resource_list_wrap = never\n" +
      "smart_tabs = false\n" +
      "space_after_closing_angle_bracket_in_type_argument = false\n" +
      "space_after_colon = true\n" +
      "space_after_comma = true\n" +
      "space_after_comma_in_type_arguments = true\n" +
      "space_after_for_semicolon = true\n" +
      "space_after_quest = true\n" +
      "space_after_type_cast = true\n" +
      "space_around_additive_operators = true\n" +
      "space_around_assignment_operators = true\n" +
      "space_around_bitwise_operators = true\n" +
      "space_around_equality_operators = true\n" +
      "space_around_lambda_arrow = true\n" +
      "space_around_logical_operators = true\n" +
      "space_around_method_ref_dbl_colon = false\n" +
      "space_around_multiplicative_operators = true\n" +
      "space_around_relational_operators = true\n" +
      "space_around_shift_operators = true\n" +
      "space_around_type_bounds_in_type_parameters = true\n" +
      "space_around_unary_operator = false\n" +
      "space_before_annotation_array_initializer_left_brace = false\n" +
      "space_before_anotation_parameter_list = false\n" +
      "space_before_array_initializer_left_brace = false\n" +
      "space_before_catch_keyword = true\n" +
      "space_before_catch_left_brace = true\n" +
      "space_before_catch_parentheses = true\n" +
      "space_before_class_left_brace = true\n" +
      "space_before_colon = true\n" +
      "space_before_colon_in_foreach = true\n" +
      "space_before_comma = false\n" +
      "space_before_do_left_brace = true\n" +
      "space_before_else_keyword = true\n" +
      "space_before_else_left_brace = true\n" +
      "space_before_finally_keyword = true\n" +
      "space_before_finally_left_brace = true\n" +
      "space_before_for_left_brace = true\n" +
      "space_before_for_parentheses = true\n" +
      "space_before_for_semicolon = false\n" +
      "space_before_if_left_brace = true\n" +
      "space_before_if_parentheses = true\n" +
      "space_before_method_call_parentheses = false\n" +
      "space_before_method_left_brace = true\n" +
      "space_before_method_parentheses = false\n" +
      "space_before_opening_angle_bracket_in_type_parameter = false\n" +
      "space_before_quest = true\n" +
      "space_before_switch_left_brace = true\n" +
      "space_before_switch_parentheses = true\n" +
      "space_before_synchronized_left_brace = true\n" +
      "space_before_synchronized_parentheses = true\n" +
      "space_before_try_left_brace = true\n" +
      "space_before_try_parentheses = true\n" +
      "space_before_type_parameter_list = false\n" +
      "space_before_while_keyword = true\n" +
      "space_before_while_left_brace = true\n" +
      "space_before_while_parentheses = true\n" +
      "space_inside_one_line_enum_braces = false\n" +
      "space_within_annotation_parentheses = false\n" +
      "space_within_array_initializer_braces = false\n" +
      "space_within_braces = false\n" +
      "space_within_brackets = false\n" +
      "space_within_cast_parentheses = false\n" +
      "space_within_catch_parentheses = false\n" +
      "space_within_empty_array_initializer_braces = false\n" +
      "space_within_empty_method_call_parentheses = false\n" +
      "space_within_empty_method_parentheses = false\n" +
      "space_within_for_parentheses = false\n" +
      "space_within_if_parentheses = false\n" +
      "space_within_method_call_parentheses = false\n" +
      "space_within_method_parentheses = false\n" +
      "space_within_parentheses = false\n" +
      "space_within_switch_parentheses = false\n" +
      "space_within_synchronized_parentheses = false\n" +
      "space_within_try_parentheses = false\n" +
      "space_within_while_parentheses = false\n" +
      "spaces_within_angle_brackets = false\n" +
      "special_else_if_treatment = true\n" +
      "subclass_name_suffix = Impl\n" +
      "tab_size = 4\n" +
      "ternary_operation_signs_on_next_line = false\n" +
      "ternary_operation_wrap = never\n" +
      "test_name_suffix = Test\n" +
      "throws_keyword_wrap = never\n" +
      "throws_list_wrap = never\n" +
      "use_external_annotations = false\n" +
      "use_fq_class_names = false\n" +
      "use_single_class_imports = true\n" +
      "use_tab_character = false\n" +
      "variable_annotation_wrap = never\n" +
      "visibility = public\n" +
      "while_brace_force = never\n" +
      "while_on_new_line = false\n" +
      "wrap_comments = false\n" +
      "wrap_first_method_in_call_chain = false\n" +
      "wrap_long_lines = false\n",

      builder.toString());
  }

  public void testSetProperties() {
    final CodeStyleSettings settings = getCurrentCodeStyleSettings();
    CodeStylePropertyMapper mapper =
      LanguageCodeStyleSettingsProvider.forLanguage(JavaLanguage.INSTANCE).getPropertyMapper(settings);
    mapper.setProperty("align_group_field_declarations", "true");
    mapper.setProperty("blank_lines_after_class_header", "1");
    mapper.setProperty("brace_style", "next_line");
    mapper.setProperty("indent_size", "2");
    mapper.setProperty("javadoc_align_param_comments", "true");
    final CommonCodeStyleSettings commonJavaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    final JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    assertTrue(commonJavaSettings.ALIGN_GROUP_FIELD_DECLARATIONS);
    assertEquals(1, commonJavaSettings.BLANK_LINES_AFTER_CLASS_HEADER);
    assertEquals(CommonCodeStyleSettings.NEXT_LINE, commonJavaSettings.BRACE_STYLE);
    assertEquals(2, commonJavaSettings.getIndentOptions().INDENT_SIZE);
    assertTrue(javaSettings.JD_ALIGN_PARAM_COMMENTS);
  }

  private static boolean isPrimitiveOrString(Class type) {
    return type.isPrimitive() || type.equals(String.class);
  }

  @Override
  protected String getBasePath() {
    return PathManagerEx.getTestDataPath() + "/codeStyle";
  }
}
