// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.customization.console;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.intellij.ui.EditorCustomization;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE;

public final class ClassFinderConsoleColorsPage implements ColorSettingsPage, DisplayPrioritySortable, EditorCustomization {
  public static final TextAttributesKey
    TERMINAL_CLASS_NAME_LOG_REFERENCE = TextAttributesKey.createTextAttributesKey("TERMINAL_CLASS_NAME_LOG_REFERENCE");

  public static final TextAttributesKey
    LOG_STRING_PLACEHOLDER = TextAttributesKey.createTextAttributesKey("LOG_STRING_PLACEHOLDER", VALID_STRING_ESCAPE);

  private static final String DEMO_TEXT =
    """
      com.example.<className>ClassName</className>
            
      log.info("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", "arg1", "arg2")
      """;

  private static final AttributesDescriptor LOG_CLASS_NAME =
    new AttributesDescriptor(OptionsBundle.messagePointer("options.language.defaults.class.name"), TERMINAL_CLASS_NAME_LOG_REFERENCE);

  private static final AttributesDescriptor LOG_STRING_PLACEHOLDER_NAME =
    new AttributesDescriptor(OptionsBundle.messagePointer("options.general.color.descriptor.logging.string.placeholder"),
                             LOG_STRING_PLACEHOLDER);
  private static final AttributesDescriptor[] ATTRS = {LOG_CLASS_NAME, LOG_STRING_PLACEHOLDER_NAME,};


  private static final Map<String, TextAttributesKey> ADDITIONAL_HIGHLIGHT_DESCRIPTORS =
    Map.of("className", TERMINAL_CLASS_NAME_LOG_REFERENCE,
           "placeholder", LOG_STRING_PLACEHOLDER);

  @Override
  public @NotNull Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ADDITIONAL_HIGHLIGHT_DESCRIPTORS;
  }

  @Override
  public @NotNull String getDisplayName() {
    return JavaBundle.message("jvm.logging.configurable.display.name");
  }

  @Override
  public @NotNull Icon getIcon() {
    return PlainTextFileType.INSTANCE.getIcon();
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
    return new PlainSyntaxHighlighter();
  }

  @Override
  public @NotNull String getDemoText() {
    return DEMO_TEXT;
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.COMMON_SETTINGS;
  }

  @Override
  public @NotNull EditorColorsScheme customizeColorScheme(@NotNull EditorColorsScheme scheme) {
    return ConsoleViewUtil.updateConsoleColorScheme(scheme);
  }

  @Override
  public void customize(@NotNull EditorEx editor) {
    editor.getSettings().setCaretRowShown(false);
  }
}
