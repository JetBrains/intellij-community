// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.ImaginaryEditor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.*;

public final class HighlightManagerImpl extends HighlightManager {
  public static final int OCCURRENCE_LAYER = HighlighterLayer.SELECTION - 1;

  private static final Key<Integer> HIGHLIGHT_FLAGS_KEY = Key.create("HIGHLIGHT_FLAGS_KEY");
  private static final Key<Set<RangeHighlighter>> HIGHLIGHTER_SET_KEY = Key.create("HIGHLIGHTER_SET_KEY");

  private final Project myProject;

  public HighlightManagerImpl(Project project) {
    myProject = project;
    ApplicationManager.getApplication().getMessageBus().connect(myProject).subscribe(AnActionListener.TOPIC, new MyAnActionListener());

    DocumentListener documentListener = new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        Document document = event.getDocument();
        for (Editor editor : EditorFactory.getInstance().getEditors(document)) {
          hideHighlights(editor, HIDE_BY_TEXT_CHANGE);
        }
      }
    };
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(documentListener, myProject);
  }

  @Contract("_, true -> !null")
  private static Set<RangeHighlighter> getEditorHighlighters(@NotNull Editor editor, boolean toCreate) {
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
    }
    Set<RangeHighlighter> highlighters = editor.getUserData(HIGHLIGHTER_SET_KEY);
    if (highlighters == null && toCreate) {
      highlighters = ((UserDataHolderEx)editor).putUserDataIfAbsent(HIGHLIGHTER_SET_KEY, new HashSet<>());
    }
    return highlighters;
  }

  public @NotNull RangeHighlighter @NotNull [] getHighlighters(@NotNull Editor editor) {
    Set<RangeHighlighter> highlighters = getEditorHighlighters(editor, false);
    if (highlighters == null) return RangeHighlighter.EMPTY_ARRAY;
    return highlighters.toArray(RangeHighlighter.EMPTY_ARRAY);
  }

  @Override
  public boolean removeSegmentHighlighter(@NotNull Editor editor, @NotNull RangeHighlighter highlighter) {
    Set<RangeHighlighter> highlighters = getEditorHighlighters(editor, false);
    if (highlighters == null) return false;

    return doRemoveHighlighter(highlighters, highlighter, (MarkupModelEx)editor.getMarkupModel());
  }

  private static boolean doRemoveHighlighter(@NotNull Set<RangeHighlighter> highlighters,
                                             @NotNull RangeHighlighter highlighter,
                                             @NotNull MarkupModelEx markupModel) {
    boolean wasRemoved = highlighters.remove(highlighter);
    if (wasRemoved) {
      setHideFlags(highlighter, null);

      if (markupModel.containsHighlighter(highlighter)) {
        highlighter.dispose();
      }
    }
    return wasRemoved;
  }

  @Override
  public void addOccurrenceHighlights(@NotNull Editor editor,
                                      PsiReference @NotNull [] occurrences,
                                      @NotNull TextAttributesKey attributesKey,
                                      boolean hideByTextChange,
                                      @Nullable Collection<? super RangeHighlighter> outHighlighters) {
    if (occurrences.length == 0) return;
    int flags = HIDE_BY_ESCAPE;
    if (hideByTextChange) {
      flags |= HIDE_BY_TEXT_CHANGE;
    }
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
        addOccurrenceHighlight(textEditor, start, end, null, attributesKey, flags, outHighlighters, null);
      }
    }
    editor.getCaretModel().moveToOffset(oldOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getScrollingModel().scrollHorizontally(horizontalScrollOffset);
    editor.getScrollingModel().scrollVertically(verticalScrollOffset);
  }

  @Override
  public void addOccurrenceHighlight(@NotNull Editor editor,
                                     int start,
                                     int end,
                                     TextAttributes attributes,
                                     int flags,
                                     Collection<? super RangeHighlighter> outHighlighters,
                                     Color scrollMarkColor) {
    addOccurrenceHighlight(editor, start, end, attributes, null, flags, outHighlighters, scrollMarkColor);
  }

  @Override
  public void addOccurrenceHighlight(@NotNull Editor editor,
                                     int start,
                                     int end,
                                     TextAttributesKey attributesKey,
                                     int flags,
                                     Collection<? super RangeHighlighter> outHighlighters) {
    addOccurrenceHighlight(editor, start, end, null, attributesKey, flags, outHighlighters, null);
  }

  private static void addOccurrenceHighlight(@NotNull Editor editor,
                                             int start,
                                             int end,
                                             @Nullable TextAttributes forcedAttributes,
                                             @Nullable TextAttributesKey attributesKey,
                                             int flags,
                                             @Nullable Collection<? super RangeHighlighter> outHighlighters,
                                             @Nullable Color scrollMarkColor) {
    MarkupModelEx markupModel = (MarkupModelEx)editor.getMarkupModel();
    markupModel.addRangeHighlighterAndChangeAttributes(attributesKey, start, end, OCCURRENCE_LAYER,
                                                       HighlighterTargetArea.EXACT_RANGE, false, highlighter -> {

        addEditorHighlighterWithHideFlags(editor, highlighter, flags);

        highlighter.setVisibleIfFolded(true);
        if (outHighlighters != null) {
          outHighlighters.add(highlighter);
        }

        if (forcedAttributes != null) {
          highlighter.setTextAttributes(forcedAttributes);
        }

        if (scrollMarkColor != null) {
          highlighter.setErrorStripeMarkColor(scrollMarkColor);
        }
      });
  }

  @ApiStatus.Internal
  public static void addEditorHighlighterWithHideFlags(@NotNull Editor editor,
                                                       @NotNull RangeHighlighter highlighter,
                                                       @HideFlags @Nullable Integer flags) {
    Set<RangeHighlighter> highlighters = getEditorHighlighters(editor, true);
    highlighters.add(highlighter);
    setHideFlags(highlighter, flags);
  }

  @Override
  public void addRangeHighlight(@NotNull Editor editor,
                                int startOffset,
                                int endOffset,
                                @NotNull TextAttributesKey attributesKey,
                                boolean hideByTextChange,
                                @Nullable Collection<? super RangeHighlighter> highlighters) {
    addRangeHighlight(editor, startOffset, endOffset, null, attributesKey, hideByTextChange, false, highlighters);
  }

  @Override
  public void addRangeHighlight(@NotNull Editor editor,
                                int startOffset,
                                int endOffset,
                                @NotNull TextAttributes attributes,
                                boolean hideByTextChange,
                                @Nullable Collection<? super RangeHighlighter> highlighters) {
    addRangeHighlight(editor, startOffset, endOffset, attributes, null, hideByTextChange, false, highlighters);
  }

  @Override
  public void addRangeHighlight(@NotNull Editor editor,
                                int startOffset,
                                int endOffset,
                                @NotNull TextAttributes attributes,
                                boolean hideByTextChange,
                                boolean hideByAnyKey,
                                @Nullable Collection<? super RangeHighlighter> highlighters) {
    addRangeHighlight(editor, startOffset, endOffset, attributes, null, hideByTextChange, hideByAnyKey, highlighters);
  }

  @Override
  public void addRangeHighlight(@NotNull Editor editor,
                                int startOffset,
                                int endOffset,
                                @NotNull TextAttributesKey attributesKey,
                                boolean hideByTextChange,
                                boolean hideByAnyKey,
                                @Nullable Collection<? super RangeHighlighter> highlighters) {
    addRangeHighlight(editor, startOffset, endOffset, null, attributesKey, hideByTextChange, hideByAnyKey, highlighters);
  }

  private static void addRangeHighlight(@NotNull Editor editor,
                                        int startOffset,
                                        int endOffset,
                                        @Nullable TextAttributes attributes,
                                        @Nullable TextAttributesKey attributesKey,
                                        boolean hideByTextChange,
                                        boolean hideByAnyKey,
                                        @Nullable Collection<? super RangeHighlighter> highlighters) {
    int flags = HIDE_BY_ESCAPE;
    if (hideByTextChange) {
      flags |= HIDE_BY_TEXT_CHANGE;
    }
    if (hideByAnyKey) {
      flags |= HIDE_BY_ANY_KEY;
    }

    Color scrollMarkColor = getScrollMarkColor(attributes, editor.getColorsScheme());

    addOccurrenceHighlight(editor, startOffset, endOffset, attributes, attributesKey, flags, highlighters, scrollMarkColor);
  }

  @Override
  public void addOccurrenceHighlights(@NotNull Editor editor,
                                      PsiElement @NotNull [] elements,
                                      @NotNull TextAttributes attributes,
                                      boolean hideByTextChange,
                                      @Nullable Collection<? super RangeHighlighter> outHighlighters) {
    addOccurrenceHighlights(editor, elements, attributes, null, hideByTextChange, outHighlighters);
  }

  @Override
  public void addOccurrenceHighlights(@NotNull Editor editor,
                                      PsiElement @NotNull [] elements,
                                      @NotNull TextAttributesKey attributesKey,
                                      boolean hideByTextChange,
                                      @Nullable Collection<? super RangeHighlighter> outHighlighters) {
    addOccurrenceHighlights(editor, elements, null, attributesKey, hideByTextChange, outHighlighters);
  }

  private void addOccurrenceHighlights(@NotNull Editor editor,
                                      PsiElement @NotNull [] elements,
                                      @Nullable TextAttributes attributes,
                                      @Nullable TextAttributesKey attributesKey,
                                      boolean hideByTextChange,
                                      @Nullable Collection<? super RangeHighlighter> outHighlighters) {
    if (elements.length == 0 || editor instanceof ImaginaryEditor) return;
    int flags = HIDE_BY_ESCAPE;
    if (hideByTextChange) {
      flags |= HIDE_BY_TEXT_CHANGE;
    }

    Color scrollMarkColor = getScrollMarkColor(attributes, editor.getColorsScheme());
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
    }

    for (PsiElement element : elements) {
      TextRange range = element.getTextRange();
      range = InjectedLanguageManager.getInstance(myProject).injectedToHost(element, range);
      addOccurrenceHighlight(editor,
                             trimOffsetToDocumentSize(editor, range.getStartOffset()),
                             trimOffsetToDocumentSize(editor, range.getEndOffset()),
                             attributes, attributesKey, flags, outHighlighters, scrollMarkColor);
    }
  }

  private static int trimOffsetToDocumentSize(@NotNull Editor editor, int offset) {
    if (offset < 0) return 0;
    int textLength = editor.getDocument().getTextLength();
    return Math.min(offset, textLength);
  }

  private static @Nullable Color getScrollMarkColor(@Nullable TextAttributes attributes, @NotNull EditorColorsScheme colorScheme) {
    if (attributes == null) return null;
    if (attributes.getErrorStripeColor() != null) return attributes.getErrorStripeColor();
    if (attributes.getBackgroundColor() != null) {
      boolean isDark = ColorUtil.isDark(colorScheme.getDefaultBackground());
      return isDark ? attributes.getBackgroundColor().brighter() : attributes.getBackgroundColor().darker();
    }
    return null;
  }

  public boolean hideHighlights(@NotNull Editor editor, @HideFlags int mask) {
    Set<RangeHighlighter> highlighters = getEditorHighlighters(editor, false);
    if (highlighters == null) return false;

    List<RangeHighlighter> highlightersToRemove = new ArrayList<>();
    for (RangeHighlighter highlighter : highlighters) {
      Integer flags = getHideFlags(highlighter);
      if (flags != null && (flags & mask) != 0) {
        highlightersToRemove.add(highlighter);
      }
    }

    MarkupModelEx markupModel = (MarkupModelEx)editor.getMarkupModel();
    for (RangeHighlighter highlighter : highlightersToRemove) {
      doRemoveHighlighter(highlighters, highlighter, markupModel);
    }

    return !highlightersToRemove.isEmpty();
  }

  boolean hasHighlightersToHide(@NotNull Editor editor, @SuppressWarnings("SameParameterValue") @HideFlags int mask) {
    Set<RangeHighlighter> highlighters = getEditorHighlighters(editor, false);
    if (highlighters != null) {
      for (RangeHighlighter highlighter : highlighters) {
        Integer flags = getHideFlags(highlighter);
        if (flags != null && (flags & mask) != 0) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @return the {@link HighlightManager.HideFlags} mask used when creating the occurrence highlighter,
   * or null if the given highlighter isn't one of those created using a method from the {@link #addOccurrenceHighlight} family.
   */
  @Contract(pure = true)
  @HideFlags
  public static @Nullable Integer getHideFlags(@NotNull RangeHighlighter highlighter) {
    //noinspection MagicConstant
    return highlighter.getUserData(HIGHLIGHT_FLAGS_KEY);
  }

  private static void setHideFlags(@NotNull RangeHighlighter highlighter, @HideFlags @Nullable Integer flags) {
    highlighter.putUserData(HIGHLIGHT_FLAGS_KEY, flags);
  }

  private final class MyAnActionListener implements AnActionListener {
    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
      requestHideHighlights(event.getDataContext());
    }

    @Override
    public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
      requestHideHighlights(dataContext);
    }

    private void requestHideHighlights(@NotNull DataContext dataContext) {
      final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      if (editor == null) return;
      hideHighlights(editor, HIDE_BY_ANY_KEY);
    }
  }
}
