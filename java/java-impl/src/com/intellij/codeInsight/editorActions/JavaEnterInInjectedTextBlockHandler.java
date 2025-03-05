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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.util.PsiLiteralUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaEnterInInjectedTextBlockHandler extends EnterHandlerDelegateAdapter {
  private @Nullable RangeMarker myRangeMarker;
  private @Nullable String myPreviousIndent;

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
    if (originalEditor == editor) return Result.Continue;
    PsiLanguageInjectionHost hostElement = InjectedLanguageManager.getInstance(project).getInjectionHost(file);
    if (!(hostElement instanceof PsiLiteralExpression literalExpression &&
          literalExpression.getLanguage() == JavaLanguage.INSTANCE &&
          literalExpression.isTextBlock())) {
      return Result.Continue;
    }
    int originalOffset = originalEditor.getCaretModel().getOffset();
    int endIndex = originalOffset - hostElement.getTextRange().getStartOffset();
    String text = hostElement.getText();
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

  @Override
  public Result postProcessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull DataContext dataContext) {
    RangeMarker rangeMarker = myRangeMarker;
    String previousIndent = myPreviousIndent;
    myPreviousIndent = null;
    myRangeMarker = null;
    if (rangeMarker == null || previousIndent == null) return Result.Continue;
    Document document = file.getFileDocument();
    if (!(document instanceof DocumentWindow)) return Result.Continue;
    Project project = editor.getProject();
    if (project == null) return Result.Continue;
    Editor originalEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
    PsiLanguageInjectionHost hostElement = InjectedLanguageManager.getInstance(project).getInjectionHost(file);
    if (!(hostElement instanceof PsiLiteralExpression literalExpression &&
          literalExpression.getLanguage() == JavaLanguage.INSTANCE &&
          literalExpression.isTextBlock())) {
      return Result.Continue;
    }
    Document originalDocument = originalEditor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(file.getFileDocument());
    PsiDocumentManager.getInstance(project).commitDocument(originalDocument);
    if (!rangeMarker.isValid()) return Result.Continue;
    TextRange changedRange = rangeMarker.getTextRange();
    int originalOffset = originalEditor.getCaretModel().getOffset();
    int lineNumber = originalDocument.getLineNumber(originalOffset);
    if (lineNumber - 1 < 0) return Result.Continue;
    int firstChangedLineNumber = originalDocument.getLineNumber(changedRange.getStartOffset());
    int lastChangedLineNumber = originalDocument.getLineNumber(changedRange.getEndOffset());
    boolean hasChanges = false;
    for (int i = firstChangedLineNumber + 1; i <= lastChangedLineNumber; i++) {
      int lineStartOffset = originalDocument.getLineStartOffset(i);
      hasChanges = true;
      originalDocument.replaceString(lineStartOffset, lineStartOffset, previousIndent);
    }
    return hasChanges ? Result.Stop : Result.Continue;
  }
}
