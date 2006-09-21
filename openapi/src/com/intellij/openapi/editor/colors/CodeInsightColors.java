/**
 * @author Yura Cangea
 */
/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.editor.colors;

public interface CodeInsightColors {
  TextAttributesKey WRONG_REFERENCES_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("WRONG_REFERENCES_ATTRIBUTES");
  TextAttributesKey ERRORS_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("ERRORS_ATTRIBUTES");
  TextAttributesKey WARNINGS_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("WARNING_ATTRIBUTES");
  TextAttributesKey GENERIC_SERVER_ERROR_OR_WARNING = TextAttributesKey.createTextAttributesKey("GENERIC_SERVER_ERROR_OR_WARNING");
  TextAttributesKey INFO_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("INFO_ATTRIBUTES");
  TextAttributesKey INFORMATION_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("INFORMATION_ATTRIBUTES");
  TextAttributesKey NOT_USED_ELEMENT_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("NOT_USED_ELEMENT_ATTRIBUTES");
  TextAttributesKey DEPRECATED_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("DEPRECATED_ATTRIBUTES");

  TextAttributesKey LOCAL_VARIABLE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("LOCAL_VARIABLE_ATTRIBUTES");
  TextAttributesKey REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES");
  TextAttributesKey REASSIGNED_PARAMETER_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("REASSIGNED_PARAMETER_ATTRIBUTES");
  TextAttributesKey INSTANCE_FIELD_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("INSTANCE_FIELD_ATTRIBUTES");
  TextAttributesKey STATIC_FIELD_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("STATIC_FIELD_ATTRIBUTES");
  TextAttributesKey PARAMETER_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("PARAMETER_ATTRIBUTES");
  TextAttributesKey CLASS_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("CLASS_NAME_ATTRIBUTES");
  TextAttributesKey TYPE_PARAMETER_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("TYPE_PARAMETER_NAME_ATTRIBUTES");
  TextAttributesKey INTERFACE_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("INTERFACE_NAME_ATTRIBUTES");
  TextAttributesKey ABSTRACT_CLASS_NAME_ATTRIBUTES = TextAttributesKey.createTextAttributesKey("ABSTRACT_CLASS_NAME_ATTRIBUTES");
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
  ColorKey LINE_FULL_COVERAGE = ColorKey.createColorKey("LINE_FULL_COVERAGE");
  ColorKey LINE_PARTIAL_COVERAGE = ColorKey.createColorKey("LINE_PARTIAL_COVERAGE");
  ColorKey LINE_NONE_COVERAGE = ColorKey.createColorKey("LINE_NONE_COVERAGE");
}
