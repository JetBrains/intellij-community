// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.RuntimeFlagsKt;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.actionSystem.ActionPlan;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import java.util.Set;


final class TypedCharImpl {
  private static final Set<Character> COMPLEX_CHARS = Set.of('\n', '\t', '(', ')', '<', '>', '[', ']', '{', '}', '"', '\'');

  static boolean beforeCharTyped(
    @NotNull Editor editor,
    @NotNull DataContext context,
    @NotNull ActionPlan plan,
    char ch
  ) {
    if (COMPLEX_CHARS.contains(ch) || Character.isSurrogate(ch)) {
      return false;
    }
    if (isImmediatePaintingDisabled(editor, ch, context)) {
      return false;
    }
    if (editor.isInsertMode()) {
      int offset = plan.getCaretOffset();
      plan.replace(offset, offset, String.valueOf(ch));
    }
    return true;
  }

  static void typeChar(@NotNull Editor editor, @NotNull Project project, char charTyped) {
    CommandProcessor.getInstance().setCurrentCommandName(EditorBundle.message("typing.in.editor.command.name"));
    EditorModificationUtilEx.insertStringAtCaret(editor, String.valueOf(charTyped), true, true);
    ((UndoManagerImpl)UndoManager.getInstance(project)).addDocumentAsAffected(editor.getDocument());
  }

  static @NotNull FileType getFileType(@NotNull PsiFile file, @NotNull Editor editor) {
    FileType fileType = file.getFileType();
    if (RuntimeFlagsKt.isEditorLockFreeTypingEnabled()) {
      // TODO: rework for lock-free typing, getLanguageInEditor (findLanguageFromElement) requires RA on EDT
      return fileType;
    }
    Language language = PsiUtilBase.getLanguageInEditor(editor, file.getProject());
    if (language != null && language != PlainTextLanguage.INSTANCE) {
      LanguageFileType associatedFileType = language.getAssociatedFileType();
      if (associatedFileType != null) {
        fileType = associatedFileType;
      }
    }
    return fileType;
  }

  private static boolean isImmediatePaintingDisabled(@NotNull Editor editor, char c, @NotNull DataContext context) {
    for (TypedHandlerDelegate delegate : TypedHandlerDelegate.EP_NAME.getExtensionList()) {
      if (!delegate.isImmediatePaintingEnabled(editor, c, context)) {
        return true;
      }
    }
    return false;
  }
}
