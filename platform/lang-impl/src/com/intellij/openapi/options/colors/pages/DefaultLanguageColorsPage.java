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
package com.intellij.openapi.options.colors.pages;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Allows to set default colors for multiple languages.
 *
 * @author Rustam Vishnyakov
 */
public class DefaultLanguageColorsPage implements ColorSettingsPage, DisplayPrioritySortable {

  private static final Map<String, TextAttributesKey> TAG_HIGHLIGHTING_MAP = new HashMap<String, TextAttributesKey>();

  static {
    TAG_HIGHLIGHTING_MAP.put("template_language", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR);
    TAG_HIGHLIGHTING_MAP.put("identifier", DefaultLanguageHighlighterColors.IDENTIFIER);
    TAG_HIGHLIGHTING_MAP.put("number", DefaultLanguageHighlighterColors.NUMBER);
    TAG_HIGHLIGHTING_MAP.put("keyword", DefaultLanguageHighlighterColors.KEYWORD);
    TAG_HIGHLIGHTING_MAP.put("string", DefaultLanguageHighlighterColors.STRING);
    TAG_HIGHLIGHTING_MAP.put("line_comment", DefaultLanguageHighlighterColors.LINE_COMMENT);
    TAG_HIGHLIGHTING_MAP.put("operation_sign", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    TAG_HIGHLIGHTING_MAP.put("braces", DefaultLanguageHighlighterColors.BRACES);
  }

  private static final AttributesDescriptor[] ATTRIBUTES_DESCRIPTORS = {
    DefaultLanguageHighlighterColors.createAttributeDescriptor(DefaultLanguageHighlighterColors.KEYWORD),
    DefaultLanguageHighlighterColors.createAttributeDescriptor(DefaultLanguageHighlighterColors.IDENTIFIER),
    DefaultLanguageHighlighterColors.createAttributeDescriptor(DefaultLanguageHighlighterColors.STRING),
    DefaultLanguageHighlighterColors.createAttributeDescriptor(DefaultLanguageHighlighterColors.NUMBER),
    DefaultLanguageHighlighterColors.createAttributeDescriptor(DefaultLanguageHighlighterColors.OPERATION_SIGN),
    DefaultLanguageHighlighterColors.createAttributeDescriptor(DefaultLanguageHighlighterColors.LINE_COMMENT),
    DefaultLanguageHighlighterColors.createAttributeDescriptor(DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR),
    DefaultLanguageHighlighterColors.createAttributeDescriptor(DefaultLanguageHighlighterColors.BRACES),
  };

  @Nullable
  @Override
  public Icon getIcon() {
    return FileTypes.PLAIN_TEXT.getIcon();
  }

  @NotNull
  @Override
  public SyntaxHighlighter getHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  @NotNull
  @Override
  public String getDemoText() {
    return
      "<keyword>Keyword</keyword>\n" +
      "<identifier>Identifier</identifier>\n" +
      "<string>'String'</string>\n" +
      "<number>12345</number>\n" +
      "<operation_sign>Operator</operation_sign>\n" +
      "<braces>{</braces> Braces <braces>}</braces>\n" +
      "<line_comment>// Line comment</line_comment>\n" +
      "<template_language>{% Template language %}</template_language>";
  }

  @Nullable
  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return TAG_HIGHLIGHTING_MAP;
  }

  @NotNull
  @Override
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRIBUTES_DESCRIPTORS;
  }

  @NotNull
  @Override
  public ColorDescriptor[] getColorDescriptors() {
    return new ColorDescriptor[0];
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Language Defaults";
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.GENERAL_SETTINGS;
  }
}
