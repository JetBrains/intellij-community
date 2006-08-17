/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;

/**
 * @author max
 */
public abstract class HighlightManager {
  public static HighlightManager getInstance(Project project) {
    return project.getComponent(HighlightManager.class);
  }

  public static final int HIDE_BY_ESCAPE = 0x01;
  public static final int HIDE_BY_ANY_KEY = 0x02;
  public static final int HIDE_BY_TEXT_CHANGE = 0x04;

  public abstract void addRangeHighlight(Editor editor,
                                         int startOffset,
                                         int endOffset,
                                         TextAttributes attributes,
                                         boolean hideByTextChange,
                                         @Nullable Collection<RangeHighlighter> outHighlighters);

  public abstract boolean removeSegmentHighlighter(Editor editor, RangeHighlighter highlighter);

  public abstract void addOccurrenceHighlights(Editor editor, PsiReference[] occurrences,
                                               TextAttributes attributes, boolean hideByTextChange,
                                               Collection<RangeHighlighter> outHighlighters);

  public abstract void addOccurrenceHighlight(Editor editor,
                                              int start,
                                              int end,
                                              TextAttributes attributes,
                                              int flags,
                                              @Nullable Collection<RangeHighlighter> outHighlighters,
                                              @Nullable Color scrollmarkColor);

  public abstract void addOccurrenceHighlights(Editor editor, PsiElement[] elements,
                                               TextAttributes attributes, boolean hideByTextChange,
                                               @Nullable Collection<RangeHighlighter> outHighlighters);

  public abstract void addElementsOccurrenceHighlights(Editor editor, PsiElement[] elements,
                                                       TextAttributes attributes, boolean hideByTextChange,
                                                       @Nullable Collection<RangeHighlighter> outHighlighters);
}
