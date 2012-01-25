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
package com.intellij.openapi.options.colors;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

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
   * surrounded by XML-like tags, for example: <code>&lt;class&gt;MyClass&lt;/class&gt;</code>.
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
}