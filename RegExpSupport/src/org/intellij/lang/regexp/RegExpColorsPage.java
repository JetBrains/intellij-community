/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.intellij.lang.regexp;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

/**
 * @author traff
 */
public class RegExpColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[] {
    new AttributesDescriptor("Keyword",  RegExpHighlighter.META),
    new AttributesDescriptor("Escaped character",  RegExpHighlighter.ESC_CHARACTER),
    new AttributesDescriptor("Invalid escape sequence",  RegExpHighlighter.INVALID_CHARACTER_ESCAPE),
    new AttributesDescriptor("Redundant escape sequence",  RegExpHighlighter.REDUNDANT_ESCAPE),
    new AttributesDescriptor("Brace",  RegExpHighlighter.BRACES),
    new AttributesDescriptor("Bracket",  RegExpHighlighter.BRACKETS),
    new AttributesDescriptor("Parenthesis",  RegExpHighlighter.PARENTHS),
    new AttributesDescriptor("Comma",  RegExpHighlighter.COMMA),
    new AttributesDescriptor("Bad character",  RegExpHighlighter.BAD_CHARACTER),
    new AttributesDescriptor("Character class",  RegExpHighlighter.CHAR_CLASS),
    new AttributesDescriptor("Quote character",  RegExpHighlighter.QUOTE_CHARACTER),
    new AttributesDescriptor("Comment",  RegExpHighlighter.COMMENT)
  };

  @NonNls private static final HashMap<String,TextAttributesKey> ourTagToDescriptorMap = new HashMap<>();

  @NotNull
  public String getDisplayName() {
    return "RegExp";
  }

  public Icon getIcon() {
    return RegExpFileType.INSTANCE.getIcon();
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    final SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(RegExpFileType.INSTANCE, null, null);
    assert highlighter != null;
    return highlighter;
  }

  @NotNull
  public String getDemoText() {
    return
      "^[\\w\\.-]+@([\\w\\-]+\\.)+[A-Z]{2,4}\\x0g\\#\\p{alpha}\\Q\\E$";

  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourTagToDescriptorMap;
  }
}
