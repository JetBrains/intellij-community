// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.completion.NewRdCompletionSupport;
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.statistic.collectors.fus.TypingEventsLogger;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.ActionPlan;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;


public final class TypedHandler extends TypedActionHandlerBase {

  @FunctionalInterface
  interface TypedDelegateFunc {
    TypedHandlerDelegate.Result call(
      @NotNull TypedHandlerDelegate delegate,
      char charTyped,
      @NotNull Project project,
      @NotNull Editor editor,
      @NotNull PsiFile file
    );
  }

  public static boolean handleRParen(@NotNull Editor editor, @NotNull FileType fileType, char charTyped) {
    return new TypedParenImpl().beforeRParenTyped(fileType, editor, charTyped);
  }

  public static void indentOpenedBrace(@NotNull Project project, @NotNull Editor editor) {
    new TypedParenImpl().indentOpenedBrace(project, editor);
  }

  public static void indentBrace(@NotNull Project project, @NotNull Editor editor, char braceChar) {
    new TypedParenImpl().indentBrace(project, editor, braceChar);
  }

  /**
   * Note: If you want to implement autopopup for an arbitrary character, consider adding your own {@link TypedHandlerDelegate}
   * and implement {@link TypedHandlerDelegate#checkAutoPopup}
   */
  public static void autoPopupCompletion(@NotNull Editor editor, char charTyped, @NotNull Project project, @NotNull PsiFile file) {
    TypedAutoPopupImpl.autoPopupCompletion(editor, charTyped, project, file);
  }

  public static void commitDocumentIfCurrentCaretIsNotTheFirstOne(@NotNull Editor editor, @NotNull Project project) {
    if (ContainerUtil.getFirstItem(editor.getCaretModel().getAllCarets()) != editor.getCaretModel().getCurrentCaret()) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    }
  }

  public static @NotNull Editor injectedEditorIfCharTypedIsSignificant(
    char charTyped,
    @NotNull Editor editor,
    @NotNull PsiFile oldFile
  ) {
    return injectedEditorIfCharTypedIsSignificant((int)charTyped, editor, oldFile);
  }

  public static @Nullable QuoteHandler getQuoteHandler(@NotNull PsiFile file, @NotNull Editor editor) {
    return TypedQuoteImpl.getQuoteHandler(file, editor);
  }

  /**
   * @deprecated use {@link QuoteHandlerEP}
   */
  @Deprecated
  public static void registerQuoteHandler(@NotNull FileType fileType, @NotNull QuoteHandler quoteHandler) {
    TypedQuoteImpl.registerQuoteHandler(fileType, quoteHandler);
  }

  @ApiStatus.Internal
  public static boolean handleQuote(@NotNull Project project, @NotNull Editor editor, char quote, @NotNull PsiFile file) {
    return new TypedQuoteImpl().handleQuote(project, file, editor, quote);
  }

  private final TypedDelegateImpl myDelegateNotifier = new TypedDelegateImpl();
  private final TypedQuoteImpl myQuoteHandler = new TypedQuoteImpl(myDelegateNotifier);
  private final TypedParenImpl myParenHandler = new TypedParenImpl(myDelegateNotifier);

  public TypedHandler(TypedActionHandler originalHandler) {
    super(originalHandler);
  }

  @Override
  public void beforeExecute(@NotNull Editor editor, char c, @NotNull DataContext context, @NotNull ActionPlan plan) {
    if (TypedCharImpl.beforeCharTyped(editor, context, plan, c)) {
      super.beforeExecute(editor, c, context, plan);
    }
  }

  @Override
  public void execute(@NotNull Editor originalEditor, char charTyped, @NotNull DataContext dataContext) {
    try (var ignored = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      doExecute(originalEditor, charTyped, dataContext);
    }
  }

  private void doExecute(@NotNull Editor originalEditor, char charTyped, @NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    PsiFile originalFile = project == null ? null : PsiUtilBase.getPsiFileInEditor(originalEditor, project);
    if (originalFile == null) {
      NewRdCompletionSupport.getInstance().noPsiAvailable(originalEditor);
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(originalEditor, charTyped, dataContext);
      }
      return;
    }
    if (!EditorModificationUtil.checkModificationAllowed(originalEditor)) {
      return;
    }
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    Document originalDocument = originalEditor.getDocument();
    myDelegateNotifier.fireNewTypingStarted(originalEditor, dataContext, charTyped);
    originalEditor.getCaretModel().runForEachCaret(caret -> {
      doExecutePerCaret(
        project,
        psiDocumentManager,
        originalFile,
        originalDocument,
        originalEditor,
        dataContext,
        caret,
        charTyped
      );
    });
  }

  private void doExecutePerCaret(
    @NotNull Project project,
    @NotNull PsiDocumentManager psiDocumentManager,
    @NotNull PsiFile originalFile,
    @NotNull Document originalDocument,
    @NotNull Editor originalEditor,
    @NotNull DataContext dataContext,
    Caret caret,
    char charTyped
  ) {
    if (psiDocumentManager.isDocumentBlockedByPsi(originalDocument)) {
      psiDocumentManager.doPostponedOperationsAndUnblockDocument(originalDocument); // to clean up after previous caret processing
    }
    Editor editor = injectedEditorIfCharTypedIsSignificant(charTyped, originalEditor, originalFile);
    PsiFile file = editor == originalEditor ? originalFile : Objects.requireNonNull(psiDocumentManager.getPsiFile(editor.getDocument()));
    try {
      if (caret == originalEditor.getCaretModel().getPrimaryCaret()) {
        boolean handled = myDelegateNotifier.fireCheckAutoPopup(project, file, editor, charTyped);
        if (!handled) {
          TypedAutoPopupImpl.autoPopupCompletion(editor, charTyped, project, file);
          TypedAutoPopupImpl.autoPopupParameterInfo(editor, charTyped, project, file);
        }
      }
      if (editor instanceof EditorWindow editorWindow && !editorWindow.isValid()) {
        // delegate must have invalidated injected editor by calling commitDocument() or similar
        editor = injectedEditorIfCharTypedIsSignificant(charTyped, originalEditor, originalFile);
        file = editor == originalEditor ? originalFile : Objects.requireNonNull(psiDocumentManager.getPsiFile(editor.getDocument()));
      }
      if (!editor.isInsertMode()) {
        TypedCharImpl.typeChar(originalEditor, project, charTyped);
        return;
      }
      if (myDelegateNotifier.fireBeforeSelectionRemoved(project, file, editor, charTyped)) {
        return;
      }
      deleteSelectedText(project, file, editor);
      FileType fileType = TypedCharImpl.getFileType(file, editor);
      if (myDelegateNotifier.fireBeforeCharTyped(project, fileType, originalFile, file, originalEditor, editor, charTyped)) {
        return;
      }
      if (myParenHandler.beforeParenTyped(fileType, editor, charTyped)) {
        return;
      }
      if (myQuoteHandler.beforeQuoteTyped(project, file, editor, charTyped)) {
        return;
      }
      long modStampBefore = editor.getDocument().getModificationStamp();
      TypedCharImpl.typeChar(originalEditor, project, charTyped);
      AutoHardWrapHandler.getInstance().wrapLineIfNecessary(originalEditor, dataContext, modStampBefore);
      if (editor.isDisposed()) { // can be that injected editor disappear
        return;
      }
      myParenHandler.afterParenTyped(project, fileType, file, editor, charTyped);
      if (myDelegateNotifier.fireCharTyped(project, file, editor, charTyped)) {
        return;
      }
      myParenHandler.indentOpenedParen(project, editor, charTyped);
    } finally {
      myDelegateNotifier.resetCompletionPhase(editor);
    }
  }

  static @NotNull Editor injectedEditorIfCharTypedIsSignificant(int charTyped, @NotNull Editor editor, @NotNull PsiFile oldFile) {
    int offset = editor.getCaretModel().getOffset();
    // even for uncommitted document try to retrieve injected fragment that has been there recently
    // we are assuming here that when user is (even furiously) typing, injected language would not change
    // and thus we can use its lexer to insert closing braces etc
    List<DocumentWindow> injected = InjectedLanguageManager.getInstance(oldFile.getProject())
      .getCachedInjectedDocumentsInRange(oldFile, ProperTextRange.create(offset, offset));
    for (DocumentWindow documentWindow : injected) {
      if (documentWindow.isValid() && documentWindow.containsRange(offset, offset)) {
        PsiFile injectedFile = PsiDocumentManager.getInstance(oldFile.getProject()).getPsiFile(documentWindow);
        if (injectedFile != null) {
          Editor injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(editor, injectedFile);
          // IDEA-52375/WEB-9105/KTNB-470 fix: last quote in editable fragment should be handled by outer language quote handler,
          // except injection-first editors
          TextRange hostRange = documentWindow.getHostRange(offset);
          CharSequence sequence = editor.getDocument().getCharsSequence();
          if (sequence.length() > offset && charTyped != Character.codePointAt(sequence, offset) ||
              hostRange != null && (
                hostRange.contains(offset) ||
                hostRange.containsOffset(offset) && !CodeFormatterFacade.shouldDelegateToTopLevel(injectedFile)
              )) {
            return injectedEditor;
          }
        }
      }
    }
    return editor;
  }

  private static void deleteSelectedText(
    @NotNull Project project,
    @NotNull PsiFile file,
    @NotNull Editor editor
  ) {
    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      int selectionLength = selectionModel.getSelectionEnd() - selectionModel.getSelectionStart();
      TypingEventsLogger.logSelectionDeleted(
        project,
        editor,
        file,
        selectionLength,
        TypingEventsLogger.SelectionDeleteAction.TYPING
      );
    }
    EditorModificationUtilEx.deleteSelectedText(editor);
  }
}
