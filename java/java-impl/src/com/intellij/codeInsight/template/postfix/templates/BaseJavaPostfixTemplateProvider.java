// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * What every postfix template provider for Java shares: the Java parser only sees a complete expression statement when
 * a semicolon follows it, so a provider has to add one before it checks a template or expands it.
 * <p>
 * A subclass adds its own templates. It overrides a method here only when it needs more, the way
 * {@link JavaPostfixTemplateProvider} extends {@link #preCheck} for a code fragment.
 */
@ApiStatus.Internal
public abstract class BaseJavaPostfixTemplateProvider implements PostfixTemplateProvider {

  @Override
  public boolean isTerminalSymbol(char currentChar) {
    return currentChar == '.' || currentChar == '!';
  }

  @Override
  public void preExpand(@NotNull PsiFile file, @NotNull Editor editor) {
    ThreadingAssertions.assertEventDispatchThread();
    if (isSemicolonNeeded(file, editor)) {
      ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().runUndoTransparentAction(() -> {
        EditorModificationUtilEx.insertStringAtCaret(editor, ";", false, false);
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
      }));
    }
  }

  @Override
  public void afterExpand(@NotNull PsiFile file, @NotNull Editor editor) {
  }

  @Override
  public @NotNull PsiFile preCheck(@NotNull PsiFile copyFile, @NotNull Editor realEditor, int currentOffset) {
    return copyWithSemicolon(copyFile, realEditor, currentOffset);
  }

  @Override
  public void prepareCopyForModCommand(@NotNull PsiFile copyFile, int currentOffset) {
    Document document = copyFile.getFileDocument();
    if (JavaCompletionContributor.semicolonNeeded(copyFile, currentOffset)) {
      document.insertString(currentOffset, ";");
      PsiDocumentManager.getInstance(copyFile.getProject()).commitDocument(document);
    }
  }

  /**
   * {@code copyFile} itself, or a new copy of it that holds a semicolon at {@code currentOffset}, when the statement
   * around that offset needs one.
   */
  protected static @NotNull PsiFile copyWithSemicolon(@NotNull PsiFile copyFile,
                                                      @NotNull Editor realEditor,
                                                      int currentOffset) {
    if (!isSemicolonNeeded(copyFile, realEditor)) return copyFile;
    StringBuilder fileContentWithSemicolon = new StringBuilder(copyFile.getFileDocument().getCharsSequence());
    fileContentWithSemicolon.insert(currentOffset, ';');
    return PostfixLiveTemplate.copyFile(copyFile, fileContentWithSemicolon);
  }

  /** Whether the statement the caret of {@code editor} is in needs a semicolon for the parser to see it as complete. */
  protected static boolean isSemicolonNeeded(@NotNull PsiFile file, @NotNull Editor editor) {
    int startOffset = CompletionInitializationContext.calcStartOffset(editor.getCaretModel().getCurrentCaret());
    return JavaCompletionContributor.semicolonNeeded(file, startOffset);
  }
}
