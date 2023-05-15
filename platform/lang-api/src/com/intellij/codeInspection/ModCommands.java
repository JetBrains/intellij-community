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
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

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
   * @param elements list of elements. If list contains no elements, nothing will be executed.
   *                 If list contains only one element, the subsequent step will be executed
   *                 right away assuming that the only element is selected.
   * @param nextStep next step generator that accepts the selected element
   * @param title    user-visible title for the element selection list
   * @param <T>      type of elements
   * @return a command that displays UI, so user can select one of the PSI elements,
   * and subsequent step will be invoked after that.
   */
  public static <T extends PsiElement> @NotNull ModCommand chooser(@NotNull List<ModChooseTarget.@NotNull ListItem<@NotNull T>> elements,
                                                                   @NotNull Function<? super @NotNull T, ? extends @NotNull ModCommand> nextStep,
                                                                   @NotNull @NlsContexts.PopupTitle String title) {
    if (elements.isEmpty()) return nop();
    if (elements.size() == 1) {
      return nextStep.apply(elements.get(0).element());
    }
    return new ModChooseTarget<>(elements, nextStep, title, 0);
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
    PsiLanguageInjectionHost hostCopy;
    if (injected) {
      PsiLanguageInjectionHost host = Objects.requireNonNull(injectionManager.getInjectionHost(origFile));
      PsiFile hostFile = host.getContainingFile();
      PsiFile hostFileCopy = (PsiFile)hostFile.copy();
      PsiFile injectedFileCopy = getInjectedFileCopy(host, hostFileCopy, orig.getLanguage());
      hostCopy = injectionManager.getInjectionHost(injectedFileCopy);
      disposable = ApplicationManager.getApplication().getService(InjectionEditService.class)
        .synchronizeWithFragment(injectedFileCopy, document);
      targetFile = hostFileCopy;
      origFile = hostFile;
      positionDocument = hostFileCopy.getViewProvider().getDocument();
    } else {
      hostCopy = null;
      disposable = null;
      targetFile = copyFile;
      positionDocument = document;
    }
    EditorUpdaterImpl context = new EditorUpdaterImpl(actionContext, positionDocument, manager, document, hostCopy, copyFile);
    try {
      String oldText = targetFile.getText();
      aspect.postponeFormattingInside(
        () -> aspect.forcePostprocessFormatInside(copyFile, () -> updater.accept(copy, context)));
      manager.commitDocument(document);
      manager.doPostponedOperationsAndUnblockDocument(document);
      String newText = targetFile.getText();
      ModCommand command = oldText.equals(newText) ? new ModNothing() : new ModUpdatePsiFile(origFile, oldText, newText);
      VirtualFile origVirtualFile = origFile.getOriginalFile().getVirtualFile();
      if (origVirtualFile != null) {
        int start = -1, end = -1, caret = -1;
        if (context.mySelectionEnd <= newText.length()) {
          start = context.mySelectionStart;
          end = context.mySelectionEnd;
        }
        if (context.myCaretOffset <= newText.length()) {
          caret = context.myCaretOffset;
        }
        if (start != -1 || end != -1 || caret != -1) {
          command = command.andThen(new ModNavigate(origVirtualFile, start, end, caret));
        }
      }
      return command;
    }
    finally {
      if (disposable != null) {
        Disposer.dispose(disposable);
      }
      positionDocument.removeDocumentListener(context);
    }
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

  private static class EditorUpdaterImpl implements EditorUpdater, DocumentListener {
    private final @NotNull Document myPositionDocument;
    private final @NotNull PsiDocumentManager myManager;
    private final @NotNull Document myDocument;
    private final @Nullable PsiLanguageInjectionHost myHost;
    private final @NotNull PsiFile myCopyFile;
    private int myCaretOffset, mySelectionStart, mySelectionEnd;

    private EditorUpdaterImpl(@NotNull ModCommandAction.ActionContext actionContext,
                              @NotNull Document positionDocument,
                              @NotNull PsiDocumentManager manager,
                              @NotNull Document document,
                              @Nullable PsiLanguageInjectionHost host,
                              @NotNull PsiFile copyFile) {
      myPositionDocument = positionDocument;
      myManager = manager;
      myDocument = document;
      myHost = host;
      myCopyFile = copyFile;
      myCaretOffset = actionContext.offset();
      mySelectionStart = actionContext.selection().getStartOffset();
      mySelectionEnd = actionContext.selection().getEndOffset();
      myPositionDocument.addDocumentListener(this);
    }

    @Override
    public void select(@NotNull PsiElement element) {
      validate(element);
      myManager.doPostponedOperationsAndUnblockDocument(myDocument);
      TextRange range = element.getTextRange();
      select(range);
    }

    @Override
    public void select(@NotNull TextRange range) {
      if (myHost != null) {
        InjectedLanguageManager instance = InjectedLanguageManager.getInstance(myCopyFile.getProject());
        PsiFile file = findInjectedFile(instance, myHost);
        int start = instance.mapUnescapedOffsetToInjected(file, range.getStartOffset());
        int end = instance.mapUnescapedOffsetToInjected(file, range.getEndOffset());
        range = instance.injectedToHost(file, TextRange.create(start, end));
      }
      mySelectionStart = range.getStartOffset();
      mySelectionEnd = range.getEndOffset();
      myCaretOffset = range.getStartOffset();
    }
    
    @Override
    public void moveTo(@NotNull PsiElement element) {
      validate(element);
      myManager.doPostponedOperationsAndUnblockDocument(myDocument);
      int offset = element.getTextRange().getStartOffset();
      if (myHost != null) {
        InjectedLanguageManager instance = InjectedLanguageManager.getInstance(myCopyFile.getProject());
        PsiFile file = findInjectedFile(instance, myHost);
        offset = instance.mapUnescapedOffsetToInjected(file, offset);
        offset = instance.injectedToHost(file, offset);
      }
      myCaretOffset = offset;
    }

    private PsiFile findInjectedFile(InjectedLanguageManager instance, PsiLanguageInjectionHost host) {
      var visitor = new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        PsiFile myFile = null;
        
        @Override
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<? extends PsiLanguageInjectionHost.Shred> places) {
          if (injectedPsi.getLanguage() == myCopyFile.getLanguage()) {
            myFile = injectedPsi;
          }
        }
      };
      instance.enumerate(host, visitor);
      return Objects.requireNonNull(visitor.myFile);
    }

    @Override
    public void moveToPrevious(char ch) {
      myManager.doPostponedOperationsAndUnblockDocument(myDocument);
      String text = myPositionDocument.getText();
      int idx = text.lastIndexOf(ch, myCaretOffset);
      if (idx == -1) return;
      myCaretOffset = idx;
    }

    private void validate(@NotNull PsiElement element) {
      if (!element.isValid()) throw new IllegalArgumentException();
      if (!PsiTreeUtil.isAncestor(myCopyFile, element, false)) throw new IllegalArgumentException();
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      myCaretOffset = updateOffset(event, myCaretOffset);
      mySelectionStart = updateOffset(event, mySelectionStart);
      mySelectionEnd = updateOffset(event, mySelectionEnd);
    }

    private static int updateOffset(DocumentEvent event, int pos) {
      int moveOffset = event.getMoveOffset();
      int offset = event.getOffset();
      int oldLength = event.getOldLength();
      int newLength = event.getNewLength();
      if (moveOffset != offset) {
        if (pos >= offset && pos <= offset + oldLength) return pos - offset + moveOffset;
        if (pos >= moveOffset && pos < offset) return pos + oldLength;
        if (pos > offset + oldLength && pos < moveOffset) return pos - oldLength;
        return pos;
      }
      if (pos <= offset) return pos;
      if (pos >= offset + oldLength) return pos + newLength - oldLength;
      return offset + newLength;
    }
  }
}
