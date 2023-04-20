// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModNavigate;
import com.intellij.modcommand.ModNothing;
import com.intellij.modcommand.ModUpdatePsiFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Utility methods to create commands
 *
 * @see ModCommand
 */
@ApiStatus.Experimental
public final class ModCommands {
  /**
   * @return a command that does nothing
   */
  public static @NotNull ModCommand nop() {
    return ModNothing.NOTHING;
  }

  /**
   * @param target element to select
   * @return a command that selects given element in the editor, assuming that it's opened in the editor
   */
  public static @NotNull ModCommand select(@NotNull PsiElement target) {
    VirtualFile file = target.getContainingFile().getVirtualFile();
    TextRange range = target.getTextRange();
    return new ModNavigate(file, range.getStartOffset(), range.getEndOffset(), range.getStartOffset());
  }

  /**
   * @param orig    PsiElement to update
   * @param updater a function that accepts a non-physical copy of the supplied orig element and performs
   *                PSI write operations in background to modify this copy
   * @return a command that will perform the corresponding update to the original element
   */
  public static @NotNull ModCommand psiUpdate(@NotNull PsiElement orig, @NotNull Consumer<@NotNull PsiElement> updater) {
    return psiUpdate(orig, (e, ctx) -> updater.accept(e));
  }

  /**
   * @param orig    PsiElement to update
   * @param updater a function that accepts a non-physical copy of the supplied orig element and a context to
   *                perform additional editor operations if necessary; and performs PSI write operations in background
   *                to modify this copy
   * @return a command that will perform the corresponding update to the original element
   */
  public static @NotNull ModCommand psiUpdate(@NotNull PsiElement orig,
                                              @NotNull BiConsumer<@NotNull PsiElement, @NotNull PsiUpdateContext> updater) {
    PsiFile origFile = orig.getContainingFile();
    VirtualFile origVirtualFile = origFile.getOriginalFile().getVirtualFile();
    Project project = origFile.getProject();
    Editor origEditor =
      origVirtualFile != null && FileEditorManager.getInstance(project).getSelectedEditor(origVirtualFile) instanceof TextEditor textEditor
      ?
      textEditor.getEditor()
      : null;
    PsiFile copyFile = (PsiFile)origFile.copy();
    PsiElement copy = PsiTreeUtil.findSameElementInCopy(orig, copyFile);
    PostprocessReformattingAspect aspect = PostprocessReformattingAspect.getInstance(project);
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    Document document = copyFile.getViewProvider().getDocument();
    var context = new PsiUpdateContext() {
      @Nullable RangeMarker mySelectionRange =
        origEditor == null ? null :
        document.createRangeMarker(origEditor.getSelectionModel().getSelectionStart(), origEditor.getSelectionModel().getSelectionEnd(),
                                   true);
      @Nullable RangeMarker myCaretRange =
        origEditor == null ? null :
        document.createRangeMarker(origEditor.getCaretModel().getOffset(), origEditor.getCaretModel().getOffset(), true);

      @Override
      public void select(@NotNull PsiElement element) {
        validate(element);
        manager.doPostponedOperationsAndUnblockDocument(document);
        if (mySelectionRange != null) {
          mySelectionRange.dispose();
        }
        if (myCaretRange != null) {
          myCaretRange.dispose();
        }
        mySelectionRange = document.createRangeMarker(element.getTextRange());
        myCaretRange = document.createRangeMarker(element.getTextRange().getStartOffset(), element.getTextRange().getStartOffset());
      }

      @Override
      public void moveTo(@NotNull PsiElement element) {
        validate(element);
        manager.doPostponedOperationsAndUnblockDocument(document);
        if (myCaretRange != null) {
          myCaretRange.dispose();
        }
        myCaretRange = document.createRangeMarker(element.getTextRange().getStartOffset(), element.getTextRange().getStartOffset());
      }

      private void validate(@NotNull PsiElement element) {
        if (!element.isValid()) throw new IllegalArgumentException();
        if (!PsiTreeUtil.isAncestor(copyFile, element, false)) throw new IllegalArgumentException();
      }
    };
    aspect.postponeFormattingInside(
      () -> aspect.forcePostprocessFormatInside(copyFile, () -> updater.accept(copy, context)));
    manager.commitDocument(document);
    manager.doPostponedOperationsAndUnblockDocument(document);
    String oldText = origFile.getText();
    String newText = copyFile.getText();
    ModCommand command = oldText.equals(newText) ? new ModNothing() : new ModUpdatePsiFile(origFile, oldText, newText);
    if (origVirtualFile != null) {
      int start = -1, end = -1, caret = -1;
      if (context.mySelectionRange != null && context.mySelectionRange.getEndOffset() <= newText.length()) {
        start = context.mySelectionRange.getStartOffset();
        end = context.mySelectionRange.getEndOffset();
      }
      if (context.myCaretRange != null && context.myCaretRange.getStartOffset() <= newText.length()) {
        caret = context.myCaretRange.getStartOffset();
      }
      if (start != -1 || end != -1 || caret != -1) {
        command = command.andThen(new ModNavigate(origVirtualFile, start, end, caret));
      }
    }
    if (context.mySelectionRange != null) {
      context.mySelectionRange.dispose();
    }
    if (context.myCaretRange != null) {
      context.myCaretRange.dispose();
    }
    return command;
  }
}
