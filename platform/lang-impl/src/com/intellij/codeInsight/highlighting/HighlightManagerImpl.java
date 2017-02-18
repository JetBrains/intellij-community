/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;

public class HighlightManagerImpl extends HighlightManager {
  private final Project myProject;

  public HighlightManagerImpl(Project project) {
    myProject = project;
    ActionManagerEx.getInstanceEx().addAnActionListener(new MyAnActionListener(), myProject);

    DocumentListener documentListener = new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent event) {
        Document document = event.getDocument();
        Editor[] editors = EditorFactory.getInstance().getEditors(document);
        for (Editor editor : editors) {
          Map<RangeHighlighter, HighlightInfo> map = getHighlightInfoMap(editor, false);
          if (map == null) return;

          ArrayList<RangeHighlighter> highlightersToRemove = new ArrayList<>();
          for (RangeHighlighter highlighter : map.keySet()) {
            HighlightInfo info = map.get(highlighter);
            if (!info.editor.getDocument().equals(document)) continue;
            if (BitUtil.isSet(info.flags, HIDE_BY_TEXT_CHANGE)) {
              highlightersToRemove.add(highlighter);
            }
          }

          for (RangeHighlighter highlighter : highlightersToRemove) {
            removeSegmentHighlighter(editor, highlighter);
          }
        }
      }
    };
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(documentListener, myProject);
  }

  @Nullable
  public Map<RangeHighlighter, HighlightInfo> getHighlightInfoMap(@NotNull Editor editor, boolean toCreate) {
    if (editor instanceof EditorWindow) return getHighlightInfoMap(((EditorWindow)editor).getDelegate(), toCreate);
    Map<RangeHighlighter, HighlightInfo> map = editor.getUserData(HIGHLIGHT_INFO_MAP_KEY);
    if (map == null && toCreate) {
      map = ((UserDataHolderEx)editor).putUserDataIfAbsent(HIGHLIGHT_INFO_MAP_KEY, new HashMap<>());
    }
    return map;
  }

  @NotNull
  public RangeHighlighter[] getHighlighters(@NotNull Editor editor) {
    Map<RangeHighlighter, HighlightInfo> highlightersMap = getHighlightInfoMap(editor, false);
    if (highlightersMap == null) return RangeHighlighter.EMPTY_ARRAY;
    Set<RangeHighlighter> set = new HashSet<>();
    for (Map.Entry<RangeHighlighter, HighlightInfo> entry : highlightersMap.entrySet()) {
      HighlightInfo info = entry.getValue();
      if (info.editor.equals(editor)) set.add(entry.getKey());
    }
    return set.toArray(new RangeHighlighter[set.size()]);
  }

  private RangeHighlighter addSegmentHighlighter(@NotNull Editor editor, int startOffset, int endOffset, TextAttributes attributes, @HideFlags int flags) {
    RangeHighlighter highlighter = editor.getMarkupModel()
      .addRangeHighlighter(startOffset, endOffset, HighlighterLayer.SELECTION - 1, attributes, HighlighterTargetArea.EXACT_RANGE);
    HighlightInfo info = new HighlightInfo(editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor, flags);
    Map<RangeHighlighter, HighlightInfo> map = getHighlightInfoMap(editor, true);
    map.put(highlighter, info);
    return highlighter;
  }

  @Override
  public boolean removeSegmentHighlighter(@NotNull Editor editor, @NotNull RangeHighlighter highlighter) {
    Map<RangeHighlighter, HighlightInfo> map = getHighlightInfoMap(editor, false);
    if (map == null) return false;
    HighlightInfo info = map.get(highlighter);
    if (info == null) return false;
    MarkupModel markupModel = info.editor.getMarkupModel();
    if (((MarkupModelEx)markupModel).containsHighlighter(highlighter)) {
      highlighter.dispose();
    }
    map.remove(highlighter);
    return true;
  }

  @Override
  public void addOccurrenceHighlights(@NotNull Editor editor,
                                      @NotNull PsiReference[] occurrences,
                                      @NotNull TextAttributes attributes,
                                      boolean hideByTextChange,
                                      Collection<RangeHighlighter> outHighlighters) {
    if (occurrences.length == 0) return;
    int flags = HIDE_BY_ESCAPE;
    if (hideByTextChange) {
      flags |= HIDE_BY_TEXT_CHANGE;
    }
    Color scrollmarkColor = getScrollMarkColor(attributes);

    int oldOffset = editor.getCaretModel().getOffset();
    int horizontalScrollOffset = editor.getScrollingModel().getHorizontalScrollOffset();
    int verticalScrollOffset = editor.getScrollingModel().getVerticalScrollOffset();
    for (PsiReference occurrence : occurrences) {
      PsiElement element = occurrence.getElement();
      int startOffset = element.getTextRange().getStartOffset();
      int start = startOffset + occurrence.getRangeInElement().getStartOffset();
      int end = startOffset + occurrence.getRangeInElement().getEndOffset();
      PsiFile containingFile = element.getContainingFile();
      Project project = element.getProject();
      // each reference can reside in its own injected editor
      Editor textEditor = InjectedLanguageUtil.openEditorFor(containingFile, project);
      if (textEditor != null) {
        addOccurrenceHighlight(textEditor, start, end, attributes, flags, outHighlighters, scrollmarkColor);
      }
    }
    editor.getCaretModel().moveToOffset(oldOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getScrollingModel().scrollHorizontally(horizontalScrollOffset);
    editor.getScrollingModel().scrollVertically(verticalScrollOffset);
  }

  @Override
  public void addElementsOccurrenceHighlights(@NotNull Editor editor,
                                              @NotNull PsiElement[] elements,
                                              @NotNull TextAttributes attributes,
                                              boolean hideByTextChange,
                                              Collection<RangeHighlighter> outHighlighters) {
    addOccurrenceHighlights(editor, elements, attributes, hideByTextChange, outHighlighters);
  }

  @Override
  public void addOccurrenceHighlight(@NotNull Editor editor,
                                     int start,
                                     int end,
                                     TextAttributes attributes,
                                     int flags,
                                     Collection<RangeHighlighter> outHighlighters,
                                     Color scrollmarkColor) {
    RangeHighlighter highlighter = addSegmentHighlighter(editor, start, end, attributes, flags);
    if (outHighlighters != null) {
      outHighlighters.add(highlighter);
    }
    if (scrollmarkColor != null) {
      highlighter.setErrorStripeMarkColor(scrollmarkColor);
    }
  }

  @Override
  public void addRangeHighlight(@NotNull Editor editor,
                                int startOffset,
                                int endOffset,
                                @NotNull TextAttributes attributes,
                                boolean hideByTextChange,
                                @Nullable Collection<RangeHighlighter> highlighters) {
    addRangeHighlight(editor, startOffset, endOffset, attributes, hideByTextChange, false, highlighters);
  }

  @Override
  public void addRangeHighlight(@NotNull Editor editor,
                                int startOffset,
                                int endOffset,
                                @NotNull TextAttributes attributes,
                                boolean hideByTextChange,
                                boolean hideByAnyKey,
                                @Nullable Collection<RangeHighlighter> highlighters) {
    int flags = HIDE_BY_ESCAPE;
    if (hideByTextChange) {
      flags |= HIDE_BY_TEXT_CHANGE;
    }
    if (hideByAnyKey) {
      flags |= HIDE_BY_ANY_KEY;
    }

    Color scrollmarkColor = getScrollMarkColor(attributes);

    addOccurrenceHighlight(editor, startOffset, endOffset, attributes, flags, highlighters, scrollmarkColor);
  }

  @Override
  public void addOccurrenceHighlights(@NotNull Editor editor,
                                      @NotNull PsiElement[] elements,
                                      @NotNull TextAttributes attributes,
                                      boolean hideByTextChange,
                                      Collection<RangeHighlighter> outHighlighters) {
    if (elements.length == 0) return;
    int flags = HIDE_BY_ESCAPE;
    if (hideByTextChange) {
      flags |= HIDE_BY_TEXT_CHANGE;
    }

    Color scrollmarkColor = getScrollMarkColor(attributes);
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
    }

    for (PsiElement element : elements) {
      TextRange range = element.getTextRange();
      range = InjectedLanguageManager.getInstance(myProject).injectedToHost(element, range);
      addOccurrenceHighlight(editor,
                             trimOffsetToDocumentSize(editor, range.getStartOffset()), 
                             trimOffsetToDocumentSize(editor, range.getEndOffset()), 
                             attributes, flags, outHighlighters, scrollmarkColor);
    }
  }

  private static int trimOffsetToDocumentSize(@NotNull Editor editor, int offset) {
    if (offset < 0) return 0;
    int textLength = editor.getDocument().getTextLength();
    return offset < textLength ? offset : textLength; 
  }

  @Nullable
  private static Color getScrollMarkColor(@NotNull TextAttributes attributes) {
    if (attributes.getErrorStripeColor() != null) return attributes.getErrorStripeColor();
    if (attributes.getBackgroundColor() != null) return attributes.getBackgroundColor().darker();
    return null;
  }

  public boolean hideHighlights(@NotNull Editor editor, @HideFlags int mask) {
    Map<RangeHighlighter, HighlightInfo> map = getHighlightInfoMap(editor, false);
    if (map == null) return false;

    boolean done = false;
    ArrayList<RangeHighlighter> highlightersToRemove = new ArrayList<>();
    for (RangeHighlighter highlighter : map.keySet()) {
      HighlightInfo info = map.get(highlighter);
      if (!info.editor.equals(editor)) continue;
      if ((info.flags & mask) != 0) {
        highlightersToRemove.add(highlighter);
        done = true;
      }
    }

    for (RangeHighlighter highlighter : highlightersToRemove) {
      removeSegmentHighlighter(editor, highlighter);
    }

    return done;
  }

  private class MyAnActionListener implements AnActionListener {
    @Override
    public void beforeActionPerformed(AnAction action, final DataContext dataContext, AnActionEvent event) {
      requestHideHighlights(dataContext);
    }


    @Override
    public void afterActionPerformed(final AnAction action, final DataContext dataContext, AnActionEvent event) {
    }

    @Override
    public void beforeEditorTyping(char c, DataContext dataContext) {
      requestHideHighlights(dataContext);
    }

    private void requestHideHighlights(final DataContext dataContext) {
      final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      if (editor == null) return;
      hideHighlights(editor, HIDE_BY_ANY_KEY);
    }
  }


  private final Key<Map<RangeHighlighter, HighlightInfo>> HIGHLIGHT_INFO_MAP_KEY = Key.create("HIGHLIGHT_INFO_MAP_KEY");

  static class HighlightInfo {
    final Editor editor;
    @HideFlags final int flags;

    public HighlightInfo(Editor editor, @HideFlags int flags) {
      this.editor = editor;
      this.flags = flags;
    }
  }


}
