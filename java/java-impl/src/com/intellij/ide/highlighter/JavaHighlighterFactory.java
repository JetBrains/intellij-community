/*
 * @author max
 */
package com.intellij.ide.highlighter;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.pom.java.LanguageLevel;

public class JavaHighlighterFactory {
  public static EditorHighlighter createJavaHighlighter(EditorColorsScheme settings, LanguageLevel languageLevel){
    return HighlighterFactory.createHighlighter(new JavaFileHighlighter(languageLevel), settings);
  }
}