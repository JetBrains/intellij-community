// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.focusMode;

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

import static com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE;
import static com.intellij.openapi.editor.markup.TextAttributes.ERASE_MARKER;

public class FocusModePassFactory implements TextEditorHighlightingPassFactory {
  public FocusModePassFactory(@NotNull TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, null, false, 2);
  }

  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    return Registry.is("editor.focus.mode") && EditorUtil.isRealFileEditor(editor) ? new FocusModePass(editor, file) : null;
  }

  private static class FocusModePass extends EditorBoundHighlightingPass {
    private static final LanguageExtension<FocusModeProvider> EP_NAME = new LanguageExtension<>("com.intellij.focusModeProvider");
    private Segment focusRange;

    private FocusModePass(Editor editor, PsiFile file) {
      super(editor, file, false);
    }

    @Override
    public void doCollectInformation(@NotNull ProgressIndicator progress) {
      focusRange = calcFocusRange(progress);
    }

    private Segment calcFocusRange(@NotNull ProgressIndicator progress) {
      Caret primaryCaret = myEditor.getCaretModel().getPrimaryCaret();

      int selectionStart = primaryCaret.getSelectionStart();

      for (FocusModeProvider provider : EP_NAME.allForLanguageOrAny(myFile.getLanguage())) {
        progress.checkCanceled();
        Segment forStart = provider.calcFocusRange(selectionStart, myFile);
        if (forStart != null) {
          int selectionEnd = primaryCaret.getSelectionEnd();
          Segment forEnd = selectionStart != selectionEnd ? provider.calcFocusRange(selectionEnd, myFile) : null;
          return forEnd != null
                 ? new TextRange(forStart.getStartOffset(), forEnd.getEndOffset())
                 : forStart;
        }
      }
      return null;
    }

    private void clearFocusMode() {
      List<RangeHighlighter> data = myEditor.getUserData(EditorImpl.FOCUS_MODE_HIGHLIGHTERS);
      if (data != null) {
        for (RangeHighlighter rangeHighlighter : data) {
          myEditor.getMarkupModel().removeHighlighter(rangeHighlighter);
        }
        data.clear();
      }
    }

    @Override
    public void doApplyInformationToEditor() {
      myEditor.putUserData(EditorImpl.FOCUS_MODE_RANGE, focusRange);
      clearFocusMode();

      if (focusRange != null) {
        applyFocusMode(focusRange);
      }
    }

    private void applyFocusMode(@NotNull Segment focusRange) {
      List<RangeHighlighter> prev = myEditor.getUserData(EditorImpl.FOCUS_MODE_HIGHLIGHTERS);
      List<RangeHighlighter> myFocusModeMarkup = prev != null ? prev : ContainerUtil.newSmartList();
      EditorColorsScheme scheme = ObjectUtils.notNull(getColorsScheme(), EditorColorsManager.getInstance().getGlobalScheme());
      Color background = scheme.getDefaultBackground();
      //noinspection UseJBColor
      Color foreground = Registry.getColor(ColorUtil.isDark(background) ? "editor.focus.mode.color.dark" : "editor.focus.mode.color.light", Color.GRAY);
      TextAttributes attributes = new TextAttributes(foreground, background, null, null, Font.PLAIN);
      myEditor.putUserData(EditorImpl.FOCUS_MODE_ATTRIBUTES, attributes);

      MarkupModel markupModel = myEditor.getMarkupModel();
      int textLength = myEditor.getDocument().getTextLength();

      int before = focusRange.getStartOffset();
      int layer = 10_000;
      myFocusModeMarkup.add(markupModel.addRangeHighlighter(0, before, layer, ERASE_MARKER, EXACT_RANGE));
      myFocusModeMarkup.add(markupModel.addRangeHighlighter(0, before, layer, attributes, EXACT_RANGE));

      int end = focusRange.getEndOffset();
      myFocusModeMarkup.add(markupModel.addRangeHighlighter(end, textLength, layer, ERASE_MARKER, EXACT_RANGE));
      myFocusModeMarkup.add(markupModel.addRangeHighlighter(end, textLength, layer, attributes, EXACT_RANGE));

      myEditor.putUserData(EditorImpl.FOCUS_MODE_HIGHLIGHTERS, myFocusModeMarkup);
    }
  }
}
