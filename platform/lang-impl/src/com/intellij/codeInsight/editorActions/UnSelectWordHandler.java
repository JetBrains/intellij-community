// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class UnSelectWordHandler extends EditorActionHandler.ForEachCaret {
  private final EditorActionHandler myOriginalHandler;

  public UnSelectWordHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  public void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (file == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, caret, dataContext);
      }
      return;
    }

    PsiDocumentManager.getInstance(project).commitDocument(document);
    doAction(editor, file);
  }

  private static void doAction(@NotNull Editor editor, @NotNull PsiFile file) {
    if (!editor.getSelectionModel().hasSelection()) {
      return;
    }

    CharSequence text = editor.getDocument().getCharsSequence();

    int cursorOffset = editor.getCaretModel().getOffset();

    if (cursorOffset > 0 && cursorOffset < text.length() &&
       !Character.isJavaIdentifierPart(text.charAt(cursorOffset)) &&
       Character.isJavaIdentifierPart(text.charAt(cursorOffset - 1))) {
      cursorOffset--;
    }

    PsiElement element = file.findElementAt(cursorOffset);

    if (element instanceof PsiWhiteSpace && cursorOffset > 0) {
      PsiElement anotherElement = file.findElementAt(cursorOffset - 1);

      if (!(anotherElement instanceof PsiWhiteSpace)) {
        element = anotherElement;
      }
    }

    final TextRange selectionRange = new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
    MaxRangeProcessor rangeProcessor = new MaxRangeProcessor(selectionRange, editor, cursorOffset);
    if (element instanceof PsiWhiteSpace) {
      if (SelectWordUtil.canWhiteSpaceBeExpanded((PsiWhiteSpace) element, cursorOffset, null, editor)) {
        SelectWordUtil.processRanges(element, text, cursorOffset, editor, rangeProcessor);
      }
      PsiElement sibling = findNextSibling(element);
      if (sibling != null) {
        int siblingOffset = sibling.getTextRange().getStartOffset();
        rangeProcessor.setCursorOffset(siblingOffset);
        SelectWordUtil.processRanges(sibling, text, siblingOffset, editor, rangeProcessor);
      }
    } else {
      SelectWordUtil.processRanges(element, text, cursorOffset, editor, rangeProcessor);
    }

    TextRange range = rangeProcessor.getMaximumRange();
    if (range == null) {
      editor.getSelectionModel().setSelection(cursorOffset, cursorOffset);
    }
    else {
      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    }
  }
  
  private static @Nullable PsiElement findNextSibling(PsiElement element) {
    PsiElement nextSibling = element.getNextSibling();
    if (nextSibling == null) {
      element = element.getParent();
      if (element == null || element instanceof PsiFile) {
        return null;
      }
      nextSibling = element.getNextSibling();
      if (nextSibling == null) {
        return null;
      }
    }
    return nextSibling;
  }
  
  private static final class MaxRangeProcessor implements Processor<TextRange> {
    private final Ref<TextRange> maximumRange;
    private final TextRange selectionRange;
    private final Editor editor;
    private int cursorOffset;

    private void setCursorOffset(int cursorOffset) {
      this.cursorOffset = cursorOffset;
    }

    private MaxRangeProcessor(TextRange selectionRange, Editor editor, int cursorOffset) {
      this.maximumRange = new Ref<>();
      this.selectionRange = selectionRange;
      this.editor = editor;
      this.cursorOffset = cursorOffset;
    }
    
    @Override
    public boolean process(TextRange range) {
      range = expandToFoldingBoundaries(range);
      if (selectionRange.contains(range) && !range.equals(selectionRange) &&
          (range.contains(cursorOffset) || cursorOffset == range.getEndOffset())) {
        if (maximumRange.get() == null || range.contains(maximumRange.get())) {
          maximumRange.set(range);
        }
      }
      return false;
    }

    private TextRange expandToFoldingBoundaries(TextRange range) {
      int startOffset = range.getStartOffset();
      FoldRegion region = editor.getFoldingModel().getCollapsedRegionAtOffset(startOffset);
      if (region != null) startOffset = region.getStartOffset();
      int endOffset = range.getEndOffset();
      region = editor.getFoldingModel().getCollapsedRegionAtOffset(endOffset);
      if (region != null && endOffset > region.getStartOffset()) endOffset = region.getEndOffset();
      return new TextRange(startOffset, endOffset);
    }

    private TextRange getMaximumRange() {
      return maximumRange.get();
    }
  }
}
