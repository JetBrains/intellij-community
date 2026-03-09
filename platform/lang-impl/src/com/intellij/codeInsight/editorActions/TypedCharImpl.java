// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.openapi.application.RuntimeFlagsKt;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

final class TypedCharImpl {
  private static final Logger LOG = Logger.getInstance(TypedCharImpl.class);

  static void type(@NotNull Editor editor, @NotNull Project project, char charTyped) {
    CommandProcessor.getInstance().setCurrentCommandName(EditorBundle.message("typing.in.editor.command.name"));
    EditorModificationUtilEx.insertStringAtCaret(editor, String.valueOf(charTyped), true, true);
    ((UndoManagerImpl)UndoManager.getInstance(project)).addDocumentAsAffected(editor.getDocument());
  }

  /**
   * @return true if any delegate requested a STOP
   */
  static boolean callDelegates(
    @NotNull TypedHandler.TypedDelegateFunc action,
    char charTyped,
    @NotNull Project project,
    @NotNull Editor editor,
    @NotNull PsiFile file
  ) {
    boolean lockFreeTyping = RuntimeFlagsKt.isEditorLockFreeTypingEnabled();
    boolean warned = false;
    for (TypedHandlerDelegate delegate : TypedHandlerDelegate.EP_NAME.getExtensionList()) {
      TypedHandlerDelegate.Result result;
      try {
        result = action.call(delegate, charTyped, project, editor, file);
      } catch (RuntimeException t) {
        if (lockFreeTyping) {
          result = TypedHandlerDelegate.Result.DEFAULT;
        } else {
          throw t;
        }
      }
      if (editor instanceof EditorWindow editorWindow && !editorWindow.isValid() && !warned) {
        LOG.warn(
          new IllegalStateException(
            ("%s has invalidated injected editor on typing char '%s'. " +
             "Please don't call commitDocument() there or other destructive methods")
              .formatted(delegate.getClass(), charTyped)
          )
        );
        warned = true;
      }
      if (result == TypedHandlerDelegate.Result.STOP) {
        return true;
      }
      if (result == TypedHandlerDelegate.Result.DEFAULT) {
        break;
      }
    }
    return false;
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
}
