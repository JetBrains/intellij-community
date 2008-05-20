/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
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
import com.intellij.util.Icons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

public class CustomColorsPage implements ColorSettingsPage {
  private static final ColorDescriptor[] COLORS = new ColorDescriptor[0];
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[] {
    new AttributesDescriptor(OptionsBundle.message("options.custom.attribute.descriptor.keyword1"), CustomHighlighterColors.CUSTOM_KEYWORD1_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.custom.attribute.descriptor.keyword2"), CustomHighlighterColors.CUSTOM_KEYWORD2_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.custom.attribute.descriptor.keyword3"), CustomHighlighterColors.CUSTOM_KEYWORD3_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.custom.attribute.descriptor.keyword4"), CustomHighlighterColors.CUSTOM_KEYWORD4_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.custom.attribute.descriptor.number"), CustomHighlighterColors.CUSTOM_NUMBER_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.custom.attribute.descriptor.string"), CustomHighlighterColors.CUSTOM_STRING_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.custom.attribute.descriptor.line.comment"), CustomHighlighterColors.CUSTOM_LINE_COMMENT_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.custom.attribute.descriptor.block.comment"), CustomHighlighterColors.CUSTOM_MULTI_LINE_COMMENT_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.custom.attribute.descriptor.valid.string.escape"), CustomHighlighterColors.CUSTOM_VALID_STRING_ESCAPE),
    new AttributesDescriptor(OptionsBundle.message("options.custom.attribute.descriptor.invalid.string.escape"), CustomHighlighterColors.CUSTOM_INVALID_STRING_ESCAPE),
  };

  @NonNls private final static SyntaxTable SYNTAX_TABLE = new SyntaxTable();
  static {
    SYNTAX_TABLE.setLineComment("#");
    SYNTAX_TABLE.setStartComment("/*");
    SYNTAX_TABLE.setEndComment("*/");
    SYNTAX_TABLE.setHexPrefix("0x");
    SYNTAX_TABLE.setNumPostfixChars("dDlL");
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

  @NotNull
  public String getDisplayName() {
    return OptionsBundle.message("options.custom.display.name");
  }

  public Icon getIcon() {
    return Icons.CUSTOM_FILE_ICON;
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return COLORS;
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new CustomFileHighlighter(SYNTAX_TABLE);
  }

  @NotNull
  public String getDemoText() {
    return "# Line comment\n"
           + "aKeyword1 variable = 123;\n"
           + "anotherKeyword1 someString = \"SomeString\";\n"
           + "aKeyword2 variable = 123;\n"
           + "anotherKeyword2 someString = \"SomeString\";\n"
           + "aKeyword3 variable = 123;\n"
           + "anotherKeyword3 someString = \"SomeString\";\n"
           + "aKeyword4 variable = 123;\n"
           + "anotherKeyword4 someString = \"SomeString\\n\\x\";\n"
           + "/* \n"
           + " * Block comment\n"
           + " */\n"
           + "\n";
  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }
}