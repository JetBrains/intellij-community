// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaEnterInInjectedTextBlockHandler extends EnterHandlerDelegateAdapter {
  @Nullable
  private RangeMarker myRangeMarker;
  @Nullable
  private String myPreviousIndent;

  @Override
  public Result preprocessEnter(@NotNull PsiFile file,
                                @NotNull Editor editor,
                                @NotNull Ref<Integer> caretOffset,
                                @NotNull Ref<Integer> caretAdvance,
                                @NotNull DataContext dataContext,
                                EditorActionHandler originalHandler) {
    Document document = file.getFileDocument();
    Project project = editor.getProject();
    if (project == null) return Result.Continue;
    if (!(document instanceof DocumentWindow)) return Result.Continue;
    Editor originalEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
    PsiFile originalFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
    if (originalFile == file || originalEditor == editor) return Result.Continue;
    if (originalFile.getLanguage() != JavaLanguage.INSTANCE) return Result.Continue;
    int originalOffset = originalEditor.getCaretModel().getOffset();
    PsiElement psiElement = originalFile.findElementAt(originalOffset);
    if (!(psiElement instanceof PsiJavaToken textBlock && textBlock.getTokenType() == JavaTokenType.TEXT_BLOCK_LITERAL)) {
      return Result.Continue;
    }
    PsiLiteralExpression literalExpression = PsiTreeUtil.getParentOfType(textBlock, PsiLiteralExpression.class);
    if (literalExpression == null) return Result.Continue;
    int endIndex = originalOffset - psiElement.getTextRange().getStartOffset();
    String text = psiElement.getText();
    if (endIndex >= text.length() || endIndex < 0) return Result.Continue;
    String firstPart = text.substring(0, endIndex);
    if (!firstPart.contains("\n")) return Result.Continue;
    Document originalDocument = originalEditor.getDocument();
    int lineNumber = originalDocument.getLineNumber(originalOffset);
    int lineStartOffset = originalDocument.getLineStartOffset(lineNumber);
    RangeMarker marker =
      originalDocument.createRangeMarker(lineStartOffset, originalDocument.getLineEndOffset(lineNumber));
    marker.setGreedyToRight(true);
    myRangeMarker = marker;
    String previousIndent = PsiLiteralUtil.getTextBlockIndentString(literalExpression);
    if (previousIndent != null && previousIndent.length() > originalOffset - lineStartOffset) {
      previousIndent = previousIndent.substring(0, originalOffset - lineStartOffset);
    }
    myPreviousIndent = previousIndent;
    return Result.Continue;
  }

  private static int getIndent(@NotNull Document document, int offset) {
    int lineNumber = document.getLineNumber(offset);
    int lineStartOffset = document.getLineStartOffset(lineNumber);
    int lineEndOffset = document.getLineEndOffset(lineNumber);
    String fullText = document.getText();
    int whiteSpaceIndex = lineStartOffset;
    while (whiteSpaceIndex < lineEndOffset && StringUtil.isWhiteSpace(fullText.charAt(whiteSpaceIndex))) {
      whiteSpaceIndex++;
    }
    return whiteSpaceIndex - lineStartOffset;
  }


  @Override
  public Result postProcessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull DataContext dataContext) {
    try {
      Document document = file.getFileDocument();
      if (!(document instanceof DocumentWindow)) return Result.Continue;
      Project project = editor.getProject();
      if (project == null) return Result.Continue;
      Editor originalEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
      PsiFile originalFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file);
      if (originalFile == file || originalEditor == editor) return Result.Continue;
      if (originalFile.getLanguage() != JavaLanguage.INSTANCE) return Result.Continue;
      if (myRangeMarker == null || myPreviousIndent == null) return Result.Continue;
      int originalOffset = originalEditor.getCaretModel().getOffset();
      PsiElement psiElement = originalFile.findElementAt(originalOffset);
      if (!(psiElement instanceof PsiJavaToken javaToken && javaToken.getTokenType() == JavaTokenType.TEXT_BLOCK_LITERAL)) {
        return Result.Continue;
      }
      Document originalDocument = originalEditor.getDocument();
      PsiDocumentManager.getInstance(project).commitDocument(file.getFileDocument());
      PsiDocumentManager.getInstance(project).commitDocument(originalDocument);
      RangeMarker rangeMarker = myRangeMarker;
      String previousIndent = myPreviousIndent;
      if (!rangeMarker.isValid()) return Result.Continue;
      TextRange changedRange = rangeMarker.getTextRange();
      int lineNumber = originalDocument.getLineNumber(originalOffset);
      if (lineNumber - 1 < 0) return Result.Continue;
      int firstChangedLineNumber = originalDocument.getLineNumber(changedRange.getStartOffset());
      int lastChangedLineNumber = originalDocument.getLineNumber(changedRange.getEndOffset());
      boolean hasChanges = false;
      for (int i = firstChangedLineNumber; i <= lastChangedLineNumber; i++) {
        int lineStartOffset = originalDocument.getLineStartOffset(i);
        if (getIndent(originalDocument, lineStartOffset) >= previousIndent.length()) {
          continue;
        }
        hasChanges = true;
        originalDocument.replaceString(lineStartOffset, lineStartOffset, previousIndent);
      }
      return hasChanges ? Result.Stop : Result.Continue;
    }
    finally {
      myPreviousIndent = null;
      myRangeMarker = null;
    }
  }
}
