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
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import org.jetbrains.annotations.NonNls;
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

  @NonNls private static final Map<String, TextAttributesKey> TAG_HIGHLIGHTING_MAP = new HashMap<String, TextAttributesKey>();

  private final static TextAttributesKey FAKE_BAD_CHAR =
    TextAttributesKey.createTextAttributesKey("FAKE_BAD_CHAR", HighlighterColors.BAD_CHARACTER);

  static {
    TAG_HIGHLIGHTING_MAP.put("bad_char", FAKE_BAD_CHAR);
    TAG_HIGHLIGHTING_MAP.put("template_language", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR);
    TAG_HIGHLIGHTING_MAP.put("identifier", DefaultLanguageHighlighterColors.IDENTIFIER);
    TAG_HIGHLIGHTING_MAP.put("number", DefaultLanguageHighlighterColors.NUMBER);
    TAG_HIGHLIGHTING_MAP.put("keyword", DefaultLanguageHighlighterColors.KEYWORD);
    TAG_HIGHLIGHTING_MAP.put("string", DefaultLanguageHighlighterColors.STRING);
    TAG_HIGHLIGHTING_MAP.put("line_comment", DefaultLanguageHighlighterColors.LINE_COMMENT);
    TAG_HIGHLIGHTING_MAP.put("block_comment", DefaultLanguageHighlighterColors.BLOCk_COMMENT);
    TAG_HIGHLIGHTING_MAP.put("operation_sign", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    TAG_HIGHLIGHTING_MAP.put("braces", DefaultLanguageHighlighterColors.BRACES);
    TAG_HIGHLIGHTING_MAP.put("doc_comment", DefaultLanguageHighlighterColors.DOC_COMMENT);
    TAG_HIGHLIGHTING_MAP.put("dot", DefaultLanguageHighlighterColors.DOT);
    TAG_HIGHLIGHTING_MAP.put("semicolon", DefaultLanguageHighlighterColors.SEMICOLON);
    TAG_HIGHLIGHTING_MAP.put("comma", DefaultLanguageHighlighterColors.COMMA);
    TAG_HIGHLIGHTING_MAP.put("brackets", DefaultLanguageHighlighterColors.BRACKETS);
    TAG_HIGHLIGHTING_MAP.put("parenths", DefaultLanguageHighlighterColors.PARENTHESES);
    TAG_HIGHLIGHTING_MAP.put("func_decl", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
    TAG_HIGHLIGHTING_MAP.put("func_call", DefaultLanguageHighlighterColors.FUNCTION_CALL);
    TAG_HIGHLIGHTING_MAP.put("param", DefaultLanguageHighlighterColors.PARAMETER);
    TAG_HIGHLIGHTING_MAP.put("class_name", DefaultLanguageHighlighterColors.CLASS_NAME);
    TAG_HIGHLIGHTING_MAP.put("class_ref", DefaultLanguageHighlighterColors.CLASS_REFERENCE);
    TAG_HIGHLIGHTING_MAP.put("inst_method", DefaultLanguageHighlighterColors.INSTANCE_METHOD);
    TAG_HIGHLIGHTING_MAP.put("inst_field", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    TAG_HIGHLIGHTING_MAP.put("static_method", DefaultLanguageHighlighterColors.STATIC_METHOD);
    TAG_HIGHLIGHTING_MAP.put("static_field", DefaultLanguageHighlighterColors.STATIC_FIELD);
    TAG_HIGHLIGHTING_MAP.put("label", DefaultLanguageHighlighterColors.LABEL);
    TAG_HIGHLIGHTING_MAP.put("local_var", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
    TAG_HIGHLIGHTING_MAP.put("global_var", DefaultLanguageHighlighterColors.GLOBAL_VARIABLE);
    TAG_HIGHLIGHTING_MAP.put("const", DefaultLanguageHighlighterColors.CONSTANT);
    TAG_HIGHLIGHTING_MAP.put("interface", DefaultLanguageHighlighterColors.INTERFACE_NAME);
    TAG_HIGHLIGHTING_MAP.put("doc_markup", DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP);
    TAG_HIGHLIGHTING_MAP.put("doc_tag", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG);
    TAG_HIGHLIGHTING_MAP.put("valid_esc_seq", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE);
    TAG_HIGHLIGHTING_MAP.put("invalid_esc_seq", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE);
    TAG_HIGHLIGHTING_MAP.put("predefined", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL);
    TAG_HIGHLIGHTING_MAP.put("metadata", DefaultLanguageHighlighterColors.METADATA);
  }

  private final static AttributesDescriptor[] ATTRIBUTES_DESCRIPTORS = {
    new AttributesDescriptor(
      OptionsBundle.message("options.java.attribute.descriptor.bad.character"), HighlighterColors.BAD_CHARACTER),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.keyword"), DefaultLanguageHighlighterColors.KEYWORD),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.identifier"), DefaultLanguageHighlighterColors.IDENTIFIER),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.string"), DefaultLanguageHighlighterColors.STRING),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.valid.esc.seq"), DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.invalid.esc.seq"), DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.number"), DefaultLanguageHighlighterColors.NUMBER),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.operation"), DefaultLanguageHighlighterColors.OPERATION_SIGN),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.braces"), DefaultLanguageHighlighterColors.BRACES),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.parentheses"), DefaultLanguageHighlighterColors.PARENTHESES),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.brackets"), DefaultLanguageHighlighterColors.BRACKETS),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.dot"), DefaultLanguageHighlighterColors.DOT),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.comma"), DefaultLanguageHighlighterColors.COMMA),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.semicolon"), DefaultLanguageHighlighterColors.SEMICOLON),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.line.comment"), DefaultLanguageHighlighterColors.LINE_COMMENT),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.block.comment"), DefaultLanguageHighlighterColors.BLOCk_COMMENT),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.doc.comment"), DefaultLanguageHighlighterColors.DOC_COMMENT),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.doc.markup"), DefaultLanguageHighlighterColors.DOC_COMMENT_MARKUP),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.doc.tag"), DefaultLanguageHighlighterColors.DOC_COMMENT_TAG),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.label"), DefaultLanguageHighlighterColors.LABEL),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.constant"), DefaultLanguageHighlighterColors.CONSTANT),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.predefined"), DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.local.variable"), DefaultLanguageHighlighterColors.LOCAL_VARIABLE),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.global.variable"), DefaultLanguageHighlighterColors.GLOBAL_VARIABLE),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.function.declaration"), DefaultLanguageHighlighterColors.FUNCTION_DECLARATION),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.function.call"), DefaultLanguageHighlighterColors.FUNCTION_CALL),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.parameter"), DefaultLanguageHighlighterColors.PARAMETER),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.interface.name"), DefaultLanguageHighlighterColors.INTERFACE_NAME),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.metadata"), DefaultLanguageHighlighterColors.METADATA),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.class.name"), DefaultLanguageHighlighterColors.CLASS_NAME),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.class.reference"), DefaultLanguageHighlighterColors.CLASS_REFERENCE),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.instance.method"), DefaultLanguageHighlighterColors.INSTANCE_METHOD),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.instance.field"), DefaultLanguageHighlighterColors.INSTANCE_FIELD),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.static.method"), DefaultLanguageHighlighterColors.STATIC_METHOD),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.static.field"), DefaultLanguageHighlighterColors.STATIC_FIELD),
    new AttributesDescriptor(
      OptionsBundle.message("options.language.defaults.template.language"), DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR),
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
      "Bad characters: <bad_char>????</bad_char>\n" +
      "<keyword>Keyword</keyword>\n" +
      "<identifier>Identifier</identifier>\n" +
      "<string>'String <valid_esc_seq>\\n</valid_esc_seq><invalid_esc_seq>\\?</invalid_esc_seq>'</string>\n" +
      "<number>12345</number>\n" +
      "<operation_sign>Operator</operation_sign>\n" +
      "Dot: <dot>.</dot> comma: <comma>,</comma> semicolon: <semicolon>;</semicolon>\n" +
      "<braces>{</braces> Braces <braces>}</braces>\n" +
      "<parenths>(</parenths> Parentheses <parenths>)</parenths>\n" +
      "<brackets>[</brackets> Brackets <brackets>]</brackets>\n" +
      "<line_comment>// Line comment</line_comment>\n" +
      "<block_comment>/* Block comment */</block_comment>\n" +
      "<doc_comment>/** \n" +
      " * Doc comment\n" +
      " * <doc_tag>@tag</doc_tag> <doc_markup><code>Markup</code></doc_markup>\n" +
      " */</doc_comment>\n" +
      "<label>:Label</label>\n" +
      "<predefined>predefined_symbol()</predefined>\n" +
      "<const>CONSTANT</const>\n" +
      "Global <global_var>variable</global_var>\n" +
      "Function <func_decl>declaration</func_decl> (<param>parameter</param>)\n" +
      "    Local <local_var>variable</local_var>\n" +
      "Function <func_call>call</func_call>()\n" +
      "Interface <interface>Name</interface>\n" +
      "<metadata>@Metadata</metadata>\n" +
      "Class <class_name>Name</class_name>\n" +
      "    instance <inst_method>method</inst_method>\n" +
      "    instance <inst_field>field</inst_field>\n" +
      "    static <static_method>method</static_method>\n" +
      "    static <static_field>field</static_field>\n" +
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
    return OptionsBundle.message("options.language.defaults.display.name");
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.GENERAL_SETTINGS;
  }
}
