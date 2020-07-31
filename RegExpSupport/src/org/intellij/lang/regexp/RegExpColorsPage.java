// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class RegExpColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[] {
    new AttributesDescriptor(RegExpBundle.message("color.settings.plain.character"), RegExpHighlighter.CHARACTER),
    new AttributesDescriptor(RegExpBundle.message("color.settings.operator.character"), RegExpHighlighter.META),
    new AttributesDescriptor(RegExpBundle.message("color.settings.escaped.character"), RegExpHighlighter.ESC_CHARACTER),
    new AttributesDescriptor(RegExpBundle.message("color.settings.invalid.escape.sequence"), RegExpHighlighter.INVALID_CHARACTER_ESCAPE),
    new AttributesDescriptor(RegExpBundle.message("color.settings.redundant.escape.sequence"), RegExpHighlighter.REDUNDANT_ESCAPE),
    new AttributesDescriptor(RegExpBundle.message("color.settings.brace"), RegExpHighlighter.BRACES),
    new AttributesDescriptor(RegExpBundle.message("color.settings.bracket"), RegExpHighlighter.BRACKETS),
    new AttributesDescriptor(RegExpBundle.message("color.settings.parenthesis"), RegExpHighlighter.PARENTHS),
    new AttributesDescriptor(RegExpBundle.message("color.settings.comma"), RegExpHighlighter.COMMA),
    new AttributesDescriptor(RegExpBundle.message("color.settings.bad.character"), RegExpHighlighter.BAD_CHARACTER),
    new AttributesDescriptor(RegExpBundle.message("color.settings.character.class"), RegExpHighlighter.CHAR_CLASS),
    new AttributesDescriptor(RegExpBundle.message("color.settings.quote.character"), RegExpHighlighter.QUOTE_CHARACTER),
    new AttributesDescriptor(RegExpBundle.message("color.settings.comment"), RegExpHighlighter.COMMENT),
    new AttributesDescriptor(RegExpBundle.message("color.settings.quantifier"), RegExpHighlighter.QUANTIFIER),
    new AttributesDescriptor(RegExpBundle.message("color.settings.dot"), RegExpHighlighter.DOT),
    new AttributesDescriptor(RegExpBundle.message("color.settings.inline.option"), RegExpHighlighter.OPTIONS),
    new AttributesDescriptor(RegExpBundle.message("color.settings.name"), RegExpHighlighter.NAME),
    new AttributesDescriptor(RegExpBundle.message("color.settings.matched.groups"), RegExpHighlighter.MATCHED_GROUPS)
  };

  @NonNls private static final HashMap<String,TextAttributesKey> ourTagToDescriptorMap = new HashMap<>();

  @Override
  @NotNull
  public String getDisplayName() {
    return RegExpBundle.message("color.settings.title.regexp");
  }

  @Override
  public Icon getIcon() {
    return RegExpFileType.INSTANCE.getIcon();
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return DESCRIPTORS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    final SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(RegExpFileType.INSTANCE, null, null);
    assert highlighter != null;
    return highlighter;
  }

  @Override
  @NotNull
  public String getDemoText() {
    return
      "^[\\w\\.-]+@([\\w\\-]+|\\.)+[A-Z0-9]{2,4}(?x)\n" +
      "\\x0g\\#\\p{Alpha}\\1(?#comment)\n" +
      ".*\\Q...\\E$# end-of-line comment";

  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourTagToDescriptorMap;
  }
}
