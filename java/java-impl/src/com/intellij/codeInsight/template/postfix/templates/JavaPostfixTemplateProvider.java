// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;


public abstract class JavaPostfixTemplateProvider implements PostfixTemplateProvider {
  @NotNull
  @Override
  public Set<PostfixTemplate> getTemplates() {
    return Collections.emptySet();
  }

  @Override
  public boolean isTerminalSymbol(char currentChar) {
    return currentChar == '.' || currentChar == '!';
  }

  @Override
  public void preExpand(@NotNull final PsiFile file, @NotNull final Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (isSemicolonNeeded(file, editor)) {
      ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().runUndoTransparentAction(() -> {
        EditorModificationUtil.insertStringAtCaret(editor, ";", false, false);
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
      }));
    }
  }

  @Override
  public void afterExpand(@NotNull final PsiFile file, @NotNull final Editor editor) {
  }

  @NotNull
  @Override
  public PsiFile preCheck(final @NotNull PsiFile copyFile, final @NotNull Editor realEditor, final int currentOffset) {
    Document document = copyFile.getViewProvider().getDocument();
    assert document != null;
    CharSequence sequence = document.getCharsSequence();
    StringBuilder fileContentWithSemicolon = new StringBuilder(sequence);
    if (isSemicolonNeeded(copyFile, realEditor)) {
      fileContentWithSemicolon.insert(currentOffset, ';');
      return PostfixLiveTemplate.copyFile(copyFile, fileContentWithSemicolon);
    }

    return copyFile;
  }

  private static boolean isSemicolonNeeded(@NotNull PsiFile file, @NotNull Editor editor) {
    int startOffset = CompletionInitializationContext.calcStartOffset(editor.getCaretModel().getCurrentCaret());
    return JavaCompletionContributor.semicolonNeeded(editor, file, startOffset);
  }
}
