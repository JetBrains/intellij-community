// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.InjectionEditService;
import com.intellij.modcommand.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
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
    PsiFile psiFile = target.getContainingFile();
    TextRange range = target.getTextRange();
    Document document = psiFile.getViewProvider().getDocument();
    if (document instanceof DocumentWindow window) {
      range = window.injectedToHost(range);
      psiFile = InjectedLanguageManager.getInstance(psiFile.getProject()).getTopLevelFile(psiFile);
    }
    VirtualFile file = psiFile.getVirtualFile();
    return new ModNavigate(file, range.getStartOffset(), range.getEndOffset(), range.getStartOffset());
  }

  /**
   * @param orig    PsiElement to update
   * @param updater a function that accepts a non-physical copy of the supplied orig element and performs
   *                PSI write operations in background to modify this copy
   * @return a command that will perform the corresponding update to the original element
   */
  public static <E extends PsiElement> @NotNull ModCommand psiUpdate(@NotNull E orig, @NotNull Consumer<@NotNull E> updater) {
    return psiUpdate(orig, (e, ctx) -> updater.accept(e));
  }

  /**
   * @param orig    PsiElement to update
   * @param updater a function that accepts a non-physical copy of the supplied orig element and a context to
   *                perform additional editor operations if necessary; and performs PSI write operations in background
   *                to modify this copy
   * @return a command that will perform the corresponding update to the original element
   */
  public static <E extends PsiElement> @NotNull ModCommand psiUpdate(@NotNull E orig,
                                                                     @NotNull BiConsumer<@NotNull E, @NotNull EditorUpdater> updater) {
    PsiFile origFile = orig.getContainingFile();
    Project project = origFile.getProject();
    ModCommandAction.ActionContext actionContext = createContext(project, origFile);
    PsiFile copyFile = actionContext.file();
    E copy = PsiTreeUtil.findSameElementInCopy(orig, copyFile);
    PostprocessReformattingAspect aspect = PostprocessReformattingAspect.getInstance(project);
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    Document document = copyFile.getViewProvider().getDocument();
    InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(project);
    Disposable disposable;
    PsiFile targetFile;
    Document positionDocument;
    boolean injected = injectionManager.isInjectedFragment(origFile);
    if (injected) {
      PsiLanguageInjectionHost host = Objects.requireNonNull(injectionManager.getInjectionHost(origFile));
      PsiFile hostFile = host.getContainingFile();
      PsiFile hostFileCopy = (PsiFile)hostFile.copy();
      PsiFile injectedFileCopy = getInjectedFileCopy(host, hostFileCopy, orig.getLanguage());
      disposable = ApplicationManager.getApplication().getService(InjectionEditService.class)
        .synchronizeWithFragment(injectedFileCopy, document);
      targetFile = hostFileCopy;
      origFile = hostFile;
      positionDocument = hostFileCopy.getViewProvider().getDocument();
    } else {
      disposable = null;
      targetFile = copyFile;
      positionDocument = document;
    }
    String oldText = targetFile.getText();
    var context = new EditorUpdaterImpl(actionContext, positionDocument, manager, document, injected, targetFile, copyFile);
    aspect.postponeFormattingInside(
      () -> aspect.forcePostprocessFormatInside(copyFile, () -> updater.accept(copy, context)));
    manager.commitDocument(document);
    manager.doPostponedOperationsAndUnblockDocument(document);
    String newText = targetFile.getText();
    ModCommand command = oldText.equals(newText) ? new ModNothing() : new ModUpdatePsiFile(origFile, oldText, newText);
    VirtualFile origVirtualFile = origFile.getOriginalFile().getVirtualFile();
    if (origVirtualFile != null) {
      int start = -1, end = -1, caret = -1;
      if (context.mySelectionRange.getEndOffset() <= newText.length()) {
        start = context.mySelectionRange.getStartOffset();
        end = context.mySelectionRange.getEndOffset();
      }
      if (context.myCaretRange.getStartOffset() <= newText.length()) {
        caret = context.myCaretRange.getStartOffset();
      }
      if (start != -1 || end != -1 || caret != -1) {
        command = command.andThen(new ModNavigate(origVirtualFile, start, end, caret));
      }
    }
    context.mySelectionRange.dispose();
    context.myCaretRange.dispose();
    if (disposable != null) {
      Disposer.dispose(disposable);
    }
    return command;
  }

  private static @NotNull PsiFile getInjectedFileCopy(@NotNull PsiLanguageInjectionHost host,
                                                      @NotNull PsiFile hostFileCopy,
                                                      @NotNull Language injectedLanguage) {
    InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(hostFileCopy.getProject());
    PsiLanguageInjectionHost hostCopy = PsiTreeUtil.findSameElementInCopy(host, hostFileCopy);
    var visitor = new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      private final Language origLanguage = injectedLanguage;
      PsiFile injectedFileCopy = null;

      @Override
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<? extends PsiLanguageInjectionHost.Shred> places) {
        if (injectedPsi.getLanguage() == origLanguage) {
          injectedFileCopy = injectedPsi;
        }
      }
    };
    injectionManager.enumerate(hostCopy, visitor);
    return Objects.requireNonNull(visitor.injectedFileCopy);
  }

  private static ModCommandAction.@NotNull ActionContext createContext(Project project, PsiFile origFile) {
    var manager = InjectedLanguageManager.getInstance(project);
    boolean injectedFragment = manager.isInjectedFragment(origFile);
    VirtualFile origVirtualFile = (injectedFragment ? manager.getTopLevelFile(origFile) : origFile).getOriginalFile().getVirtualFile();
    Editor origEditor =
      origVirtualFile != null && FileEditorManager.getInstance(project).getSelectedEditor(origVirtualFile) instanceof TextEditor textEditor
      ?
      textEditor.getEditor()
      : null;
    int offset = 0, start = 0, end = 0;
    if (origEditor != null) {
      offset = origEditor.getCaretModel().getOffset();
      start = origEditor.getSelectionModel().getSelectionStart();
      end = origEditor.getSelectionModel().getSelectionEnd();
    }
    PsiFile copyFile;
    if (!injectedFragment) {
      copyFile = (PsiFile)origFile.copy();
    } else {
      copyFile = PsiFileFactory.getInstance(project).createFileFromText(
        origFile.getName(), origFile.getFileType(), manager.getUnescapedText(origFile),
        LocalTimeCounter.currentTime(), false);
    }
    return new ModCommandAction.ActionContext(project, copyFile, offset, TextRange.create(start, end));
  }

  private static class EditorUpdaterImpl implements EditorUpdater {
    private final @NotNull Document myPositionDocument;
    private final @NotNull PsiDocumentManager myManager;
    private final @NotNull Document myDocument;
    private final boolean myInjected;
    private final @NotNull PsiFile myTargetFile;
    private final @NotNull PsiFile myCopyFile;
    @NotNull RangeMarker mySelectionRange;
    @NotNull RangeMarker myCaretRange;

    private EditorUpdaterImpl(@NotNull ModCommandAction.ActionContext actionContext,
                              @NotNull Document positionDocument,
                              @NotNull PsiDocumentManager manager,
                              @NotNull Document document,
                              boolean injected,
                              @NotNull PsiFile targetFile,
                              @NotNull PsiFile copyFile) {
      myPositionDocument = positionDocument;
      myManager = manager;
      myDocument = document;
      myInjected = injected;
      myTargetFile = targetFile;
      myCopyFile = copyFile;
      mySelectionRange = myPositionDocument.createRangeMarker(actionContext.selection().getStartOffset(),
                                                              actionContext.selection().getEndOffset(), true);
      myCaretRange = myPositionDocument.createRangeMarker(actionContext.offset(), actionContext.offset(), true);
    }

    @Override
    public void select(@NotNull PsiElement element) {
      validate(element);
      myManager.doPostponedOperationsAndUnblockDocument(myDocument);
      mySelectionRange.dispose();
      myCaretRange.dispose();
      if (myInjected) {
        element = PsiTreeUtil.findSameElementInCopy(element, myTargetFile);
      }
      mySelectionRange = myPositionDocument.createRangeMarker(element.getTextRange());
      myCaretRange = myPositionDocument.createRangeMarker(element.getTextRange().getStartOffset(), element.getTextRange().getStartOffset());
    }

    @Override
    public void moveTo(@NotNull PsiElement element) {
      validate(element);
      myManager.doPostponedOperationsAndUnblockDocument(myDocument);
      if (myInjected) {
        element = PsiTreeUtil.findSameElementInCopy(element, myTargetFile);
      }
      int offset = element.getTextRange().getStartOffset();
      moveToOffset(offset);
    }

    private void moveToOffset(int offset) {
      myCaretRange.dispose();
      myCaretRange = myPositionDocument.createRangeMarker(offset, offset);
    }

    @Override
    public void moveToPrevious(char ch) {
      myManager.doPostponedOperationsAndUnblockDocument(myDocument);
      String text = myPositionDocument.getText();
      int idx = text.lastIndexOf(ch, myCaretRange.getStartOffset());
      if (idx == -1) return;
      moveToOffset(idx);
    }

    private void validate(@NotNull PsiElement element) {
      if (!element.isValid()) throw new IllegalArgumentException();
      if (!PsiTreeUtil.isAncestor(myCopyFile, element, false)) throw new IllegalArgumentException();
    }
  }
}
