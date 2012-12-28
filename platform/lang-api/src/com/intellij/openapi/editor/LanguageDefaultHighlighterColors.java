/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.util.containers.HashMap;

import java.util.Map;

/**
 * Base highlighter colors for multiple languages.
 *
 * @author Rustam Vishnyakov
 */
public class LanguageDefaultHighlighterColors {

  private final static Map<TextAttributesKey,String> DISPLAY_NAMES_MAP = new HashMap<TextAttributesKey, String>();

  public final static TextAttributesKey TEMPLATE_LANGUAGE_COLOR =
    TextAttributesKey.createTextAttributesKey("DEFAULT_TEMPLATE_LANGUAGE_COLOR");

  public final static TextAttributesKey IDENTIFIER = TextAttributesKey.createTextAttributesKey("DEFAULT_IDENTIFIER");

  static {
    DISPLAY_NAMES_MAP.put(IDENTIFIER, "Identifier");
    DISPLAY_NAMES_MAP.put(TEMPLATE_LANGUAGE_COLOR, "Template language");
  }

  public static AttributesDescriptor createAttributeDescriptor(TextAttributesKey key) {
    String presentableName = DISPLAY_NAMES_MAP.get(key);
    if (presentableName == null) presentableName = key.getExternalName();
    return new AttributesDescriptor(presentableName, key);
  }

  public static String getDisplayName(TextAttributesKey key) {
    return DISPLAY_NAMES_MAP.containsKey(key) ? DISPLAY_NAMES_MAP.get(key) : "<" + key.getExternalName() +">";
  }
}
