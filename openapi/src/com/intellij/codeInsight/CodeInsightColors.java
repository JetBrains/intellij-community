/**
 * @author Yura Cangea
 */
package com.intellij.codeInsight;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.TextAttributesKey;

public interface CodeInsightColors {
  TextAttributesKey WRONG_REFERENCES_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("WRONG_REFERENCES_ATTRIBUTES");
  TextAttributesKey ERRORS_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("ERRORS_ATTRIBUTES");
  TextAttributesKey WARNINGS_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("WARNING_ATTRIBUTES");
  TextAttributesKey NOT_USED_ELEMENT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("NOT_USED_ELEMENT_ATTRIBUTES");
  TextAttributesKey DEPRECATED_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("DEPRECATED_ATTRIBUTES");

  TextAttributesKey LOCAL_VARIABLE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("LOCAL_VARIABLE_ATTRIBUTES");
  TextAttributesKey INSTANCE_FIELD_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("INSTANCE_FIELD_ATTRIBUTES");
  TextAttributesKey STATIC_FIELD_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("STATIC_FIELD_ATTRIBUTES");
  TextAttributesKey PARAMETER_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("PARAMETER_ATTRIBUTES");
  TextAttributesKey CLASS_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CLASS_NAME_ATTRIBUTES");
  TextAttributesKey INTERFACE_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("INTERFACE_NAME_ATTRIBUTES");
  TextAttributesKey METHOD_CALL_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("METHOD_CALL_ATTRIBUTES");
  TextAttributesKey METHOD_DECLARATION_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("METHOD_DECLARATION_ATTRIBUTES");
  TextAttributesKey STATIC_METHOD_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("STATIC_METHOD_ATTRIBUTES");
  TextAttributesKey CONSTRUCTOR_CALL_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CONSTRUCTOR_CALL_ATTRIBUTES");
  TextAttributesKey CONSTRUCTOR_DECLARATION_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CONSTRUCTOR_DECLARATION_ATTRIBUTES");
  TextAttributesKey ANNOTATION_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("ANNOTATION_NAME_ATTRIBUTES");
  TextAttributesKey ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES");
  TextAttributesKey ANNOTATION_ATTRIBUTE_VALUE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("ANNOTATION_ATTRIBUTE_VALUE_ATTRIBUTES");

  TextAttributesKey MATCHED_BRACE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("MATCHED_BRACE_ATTRIBUTES");
  TextAttributesKey UNMATCHED_BRACE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("UNMATCHED_BRACE_ATTRIBUTES");

  TextAttributesKey JOIN_POINT = TextAttributesKey.createTextAttributesKey("JOIN_POINT");
  TextAttributesKey BLINKING_HIGHLIGHTS_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("BLINKING_HIGHLIGHTS_ATTRIBUTES");
  TextAttributesKey HYPERLINK_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("HYPERLINK_ATTRIBUTES");

  // Colors
  ColorKey METHOD_SEPARATORS_COLOR = ColorKey.createColorKey("METHOD_SEPARATORS_COLOR");
}
