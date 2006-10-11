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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.TextAttributesKey;

public interface HighlighterColors {
  TextAttributesKey TEXT = TextAttributesKey.createTextAttributesKey("TEXT");
  TextAttributesKey BAD_CHARACTER = TextAttributesKey.createTextAttributesKey("BAD_CHARACTER");

  TextAttributesKey JAVA_LINE_COMMENT = TextAttributesKey.createTextAttributesKey("JAVA_LINE_COMMENT");
  TextAttributesKey JAVA_BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey("JAVA_BLOCK_COMMENT");
  TextAttributesKey JAVA_DOC_COMMENT = TextAttributesKey.createTextAttributesKey("JAVA_DOC_COMMENT");
  TextAttributesKey JAVA_DOC_TAG = TextAttributesKey.createTextAttributesKey("JAVA_DOC_TAG");
  TextAttributesKey JAVA_DOC_MARKUP = TextAttributesKey.createTextAttributesKey("JAVA_DOC_MARKUP");
  TextAttributesKey JAVA_KEYWORD = TextAttributesKey.createTextAttributesKey("JAVA_KEYWORD");
  TextAttributesKey JAVA_NUMBER = TextAttributesKey.createTextAttributesKey("JAVA_NUMBER");
  TextAttributesKey JAVA_STRING = TextAttributesKey.createTextAttributesKey("JAVA_STRING");
  TextAttributesKey JAVA_VALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey("JAVA_VALID_STRING_ESCAPE");
  TextAttributesKey JAVA_INVALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey("JAVA_INVALID_STRING_ESCAPE");
  TextAttributesKey JAVA_OPERATION_SIGN = TextAttributesKey.createTextAttributesKey("JAVA_OPERATION_SIGN");
  TextAttributesKey JAVA_PARENTHS = TextAttributesKey.createTextAttributesKey("JAVA_PARENTH");
  TextAttributesKey JAVA_BRACKETS = TextAttributesKey.createTextAttributesKey("JAVA_BRACKETS");
  TextAttributesKey JAVA_BRACES = TextAttributesKey.createTextAttributesKey("JAVA_BRACES");
  TextAttributesKey JAVA_COMMA = TextAttributesKey.createTextAttributesKey("JAVA_COMMA");
  TextAttributesKey JAVA_DOT = TextAttributesKey.createTextAttributesKey("JAVA_DOT");
  TextAttributesKey JAVA_SEMICOLON = TextAttributesKey.createTextAttributesKey("JAVA_SEMICOLON");

  TextAttributesKey XML_PROLOGUE = TextAttributesKey.createTextAttributesKey("XML_PROLOGUE");
  TextAttributesKey XML_COMMENT = TextAttributesKey.createTextAttributesKey("XML_COMMENT");
  TextAttributesKey XML_TAG = TextAttributesKey.createTextAttributesKey("XML_TAG");
  TextAttributesKey XML_TAG_NAME = TextAttributesKey.createTextAttributesKey("XML_TAG_NAME");
  TextAttributesKey XML_ATTRIBUTE_NAME = TextAttributesKey.createTextAttributesKey("XML_ATTRIBUTE_NAME");
  TextAttributesKey XML_ATTRIBUTE_VALUE = TextAttributesKey.createTextAttributesKey("XML_ATTRIBUTE_VALUE");
  TextAttributesKey XML_TAG_DATA = TextAttributesKey.createTextAttributesKey("XML_TAG_DATA");
  TextAttributesKey XML_ENTITY_REFERENCE = TextAttributesKey.createTextAttributesKey("XML_ENTITY_REFERENCE");

  TextAttributesKey HTML_COMMENT = TextAttributesKey.createTextAttributesKey("HTML_COMMENT");
  TextAttributesKey HTML_TAG = TextAttributesKey.createTextAttributesKey("HTML_TAG");
  TextAttributesKey HTML_TAG_NAME = TextAttributesKey.createTextAttributesKey("HTML_TAG_NAME");
  TextAttributesKey HTML_ATTRIBUTE_NAME = TextAttributesKey.createTextAttributesKey("HTML_ATTRIBUTE_NAME");
  TextAttributesKey HTML_ATTRIBUTE_VALUE = TextAttributesKey.createTextAttributesKey("HTML_ATTRIBUTE_VALUE");
  TextAttributesKey HTML_ENTITY_REFERENCE = TextAttributesKey.createTextAttributesKey("HTML_ENTITY_REFERENCE");

  TextAttributesKey JSP_COMMENT = TextAttributesKey.createTextAttributesKey("JSP_COMMENT");
  TextAttributesKey JSP_SCRIPTING_BACKGROUND = TextAttributesKey.createTextAttributesKey("JSP_SCRIPTING_BACKGROUND");
  TextAttributesKey JSP_ACTION_AND_DIRECTIVE_BACKGROUND = TextAttributesKey.createTextAttributesKey("JSP_DIRECTIVE_BACKGROUND");
  
  TextAttributesKey JSP_ACTION_AND_DIRECTIVE_NAME = TextAttributesKey.createTextAttributesKey("JSP_DIRECTIVE_NAME");
  TextAttributesKey JSP_ATTRIBUTE_NAME = TextAttributesKey.createTextAttributesKey("JSP_ATTRIBUTE_NAME");
  TextAttributesKey JSP_ATTRIBUTE_VALUE = TextAttributesKey.createTextAttributesKey("JSP_ATTRIBUTE_VALUE");
  TextAttributesKey JSP_EL_KEYWORDS = TextAttributesKey.createTextAttributesKey("JSP_EL_KEYWORDS");
}
