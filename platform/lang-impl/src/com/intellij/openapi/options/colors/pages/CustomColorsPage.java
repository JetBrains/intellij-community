// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.colors.pages;

import com.intellij.ide.highlighter.custom.CustomFileHighlighter;
import com.intellij.ide.highlighter.custom.CustomHighlighterColors;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

public class CustomColorsPage implements ColorSettingsPage, DisplayPrioritySortable {
  private static final AttributesDescriptor[] ATTRS = {
    new AttributesDescriptor(OptionsBundle.messagePointer("options.custom.attribute.descriptor.keyword1"), CustomHighlighterColors.CUSTOM_KEYWORD1_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.custom.attribute.descriptor.keyword2"), CustomHighlighterColors.CUSTOM_KEYWORD2_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.custom.attribute.descriptor.keyword3"), CustomHighlighterColors.CUSTOM_KEYWORD3_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.custom.attribute.descriptor.keyword4"), CustomHighlighterColors.CUSTOM_KEYWORD4_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.custom.attribute.descriptor.number"), CustomHighlighterColors.CUSTOM_NUMBER_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.custom.attribute.descriptor.string"), CustomHighlighterColors.CUSTOM_STRING_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.custom.attribute.descriptor.line.comment"), CustomHighlighterColors.CUSTOM_LINE_COMMENT_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.custom.attribute.descriptor.block.comment"), CustomHighlighterColors.CUSTOM_MULTI_LINE_COMMENT_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.custom.attribute.descriptor.valid.string.escape"), CustomHighlighterColors.CUSTOM_VALID_STRING_ESCAPE),
    new AttributesDescriptor(OptionsBundle.messagePointer("options.custom.attribute.descriptor.invalid.string.escape"), CustomHighlighterColors.CUSTOM_INVALID_STRING_ESCAPE),
  };

  private static final SyntaxTable SYNTAX_TABLE = new SyntaxTable();
  static {
    SYNTAX_TABLE.setLineComment("#");
    SYNTAX_TABLE.setStartComment("/*");
    SYNTAX_TABLE.setEndComment("*/");
    SYNTAX_TABLE.setHexPrefix("0x");
    SYNTAX_TABLE.setNumPostfixChars("dDlL");
    SYNTAX_TABLE.setHasStringEscapes(true);
    SYNTAX_TABLE.addKeyword1("aKeyword1");
    SYNTAX_TABLE.addKeyword1("anotherKeyword1");
    SYNTAX_TABLE.addKeyword2("aKeyword2");
    SYNTAX_TABLE.addKeyword2("anotherKeyword2");
    SYNTAX_TABLE.addKeyword3("aKeyword3");
    SYNTAX_TABLE.addKeyword3("anotherKeyword3");
    SYNTAX_TABLE.addKeyword4("aKeyword4");
    SYNTAX_TABLE.addKeyword4("anotherKeyword4");
  }

  @Override
  public @NotNull String getDisplayName() {
    return OptionsBundle.message("options.custom.display.name");
  }

  @Override
  public Icon getIcon() {
    return PlatformIcons.CUSTOM_FILE_ICON;
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return ATTRS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  public @NotNull SyntaxHighlighter getHighlighter() {
    return new CustomFileHighlighter(SYNTAX_TABLE);
  }

  @Override
  public @NotNull String getDemoText() {
    return "# Line comment\n"
           + "aKeyword1 variable = 123;\n"
           + "anotherKeyword1 someString = \"SomeString\";\n"
           + "aKeyword2 variable = 123;\n"
           + "anotherKeyword2 someString = \"SomeString\";\n"
           + "aKeyword3 variable = 123;\n"
           + "anotherKeyword3 someString = \"SomeString\";\n"
           + "aKeyword4 variable = 123;\n"
           + "anotherKeyword4 someString = \"SomeString \\n\\x  \\& \\g \";\n"
           + "/* \n"
           + " * Block comment\n"
           + " */\n"
           + "\n";
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.COMMON_SETTINGS;
  }
}
