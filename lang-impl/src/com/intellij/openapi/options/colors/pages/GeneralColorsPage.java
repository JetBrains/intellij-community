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

import com.intellij.codeInsight.template.impl.TemplateColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

public class GeneralColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATT_DESCRIPTORS = new AttributesDescriptor[] {
    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.default.text"), HighlighterColors.TEXT),

    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.folded.text"), EditorColors.FOLDED_TEXT_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.search.result"), EditorColors.SEARCH_RESULT_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.search.result.write.access"), EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptior.identifier.under.caret"), EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptior.identifier.under.caret.write"), EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.text.search.result"), EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES),

    new AttributesDescriptor(OptionsBundle.message("options.general.attribute.descriptor.template.variable"), TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES),
    new AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.injected.language.fragment"), EditorColors.INJECTED_LANGUAGE_FRAGMENT),
  };

  private static final ColorDescriptor[] COLOR_DESCRIPTORS = new ColorDescriptor[] {
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.background.in.readonly.files"), EditorColors.READONLY_BACKGROUND_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.deneral.color.descriptor.readonly.fragment.background"), EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.gutter.background"), EditorColors.LEFT_GUTTER_BACKGROUND, ColorDescriptor.Kind.BACKGROUND),

    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.selection.background"), EditorColors.SELECTION_BACKGROUND_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.selection.foreground"), EditorColors.SELECTION_FOREGROUND_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.caret"), EditorColors.CARET_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.caret.row"), EditorColors.CARET_ROW_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.right.margin"), EditorColors.RIGHT_MARGIN_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.whitespaces"), EditorColors.WHITESPACES_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.line.number"), EditorColors.LINE_NUMBERS_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.vcs.annotations"), EditorColors.ANNOTATIONS_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.folding.outline"), EditorColors.FOLDING_TREE_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.selected.folding.outline"), EditorColors.SELECTED_FOLDING_TREE_COLOR, ColorDescriptor.Kind.FOREGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.added.lines"), EditorColors.ADDED_LINES_COLOR, ColorDescriptor.Kind.BACKGROUND),
    new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.modified.lines"), EditorColors.MODIFIED_LINES_COLOR, ColorDescriptor.Kind.BACKGROUND),
  };

  @NotNull
  public String getDisplayName() {
    return OptionsBundle.message("options.general.display.name");
  }

  public Icon getIcon() {
    return FileTypes.PLAIN_TEXT.getIcon();
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATT_DESCRIPTORS;
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return COLOR_DESCRIPTORS;
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  @NotNull
  public String getDemoText() {
    return
      "IntelliJ IDEA is a full-featured Java IDE\n" +
      "with a high level of usability and outstanding\n" +
      "advanced code editing and refactoring support.\n";
  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }
}