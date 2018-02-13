// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.colors;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * Interface for a custom page shown in the "Colors and Fonts" settings dialog.
 */
public interface ColorSettingsPage extends ColorAndFontDescriptorsProvider {
  ExtensionPointName<ColorSettingsPage> EP_NAME = ExtensionPointName.create("com.intellij.colorSettingsPage");

  /**
   * Returns the icon for the page, shown in the dialog tab.
   *
   * @return the icon for the page, or null if the page does not have a custom icon.
   */
  @Nullable Icon getIcon();

  /**
   * Returns the syntax highlighter which is used to highlight the text shown in the preview
   * pane of the page.
   *
   * @return the syntax highlighter instance.
   */
  @NotNull SyntaxHighlighter getHighlighter();

  /**
   * Returns the text shown in the preview pane. If some elements need to be highlighted in
   * the preview text which are not highlighted by the syntax highlighter, they need to be
   * surrounded by XML-like tags, for example: {@code <class>MyClass</class>}.
   * The mapping between the names of the tags and the text attribute keys used for highlighting
   * is defined by the {@link #getAdditionalHighlightingTagToDescriptorMap()} method.
   *
   * @return the text to show in the preview pane.
   */
  @NonNls @NotNull String getDemoText();

  /**
   * Returns the mapping from special tag names surrounding the regions to be highlighted
   * in the preview text (see {@link #getDemoText()}) to text attribute keys used to
   * highlight the regions.
   *
   * @return the mapping from tag names to text attribute keys, or null if the demo text
   * does not contain any additional highlighting tags.
   */
  @Nullable Map<String,TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap();

  /**
   * Specifies tag-to-'color key' mapping for regions with presentation containing additional colors from color map. 
   * It's used to implement navigation between the list of keys and regions in sample editor.
   */
  @Nullable default Map<String, ColorKey> getAdditionalHighlightingTagToColorKeyMap() { return null; }
}