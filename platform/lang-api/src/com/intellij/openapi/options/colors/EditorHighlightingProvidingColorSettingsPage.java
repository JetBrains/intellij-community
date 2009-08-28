/*
 * @author max
 */
package com.intellij.openapi.options.colors;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;

public interface EditorHighlightingProvidingColorSettingsPage extends ColorSettingsPage {
  EditorHighlighter createEditorHighlighter(final EditorColorsScheme scheme);
}