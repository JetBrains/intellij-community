package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

import java.awt.*;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 21, 2004
 * Time: 7:56:45 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class HighlightManager {
  public static HighlightManager getInstance(Project project) {
    return project.getComponent(HighlightManager.class);
  }

  public abstract void addRangeHighlight(Editor editor,
                                int startOffset,
                                int endOffset,
                                TextAttributes attributes,
                                boolean hideByTextChange,
                                ArrayList highlighters);

  public abstract boolean removeSegmentHighlighter(Editor editor, RangeHighlighter highlighter);

  public abstract void addOccurrenceHighlights(Editor editor, PsiReference[] occurrences,
                                      TextAttributes attributes, boolean hideByTextChange,
                                      ArrayList highlightersVector);

  protected abstract void addOccurrenceHighlight(Editor editor,
                                      int start,
                                      int end,
                                      TextAttributes attributes,
                                      int flags,
                                      ArrayList highlightersVector,
                                      Color scrollmarkColor);

  public abstract void addOccurrenceHighlights(Editor editor, PsiElement[] elements,
                                      TextAttributes attributes, boolean hideByTextChange,
                                      ArrayList highlightersVector);

  public abstract void addElementsOccurrenceHighlights(Editor editor, PsiElement[] elements,
                                      TextAttributes attributes, boolean hideByTextChange,
                                      ArrayList highlightersVector);
}
