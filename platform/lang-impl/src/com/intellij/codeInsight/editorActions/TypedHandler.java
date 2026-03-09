// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.NewRdCompletionSupport;
import com.intellij.codeInsight.completion.TypedEvent;
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.statistic.collectors.fus.TypingEventsLogger;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.RuntimeFlagsKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.actionSystem.ActionPlan;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileWithOneLanguage;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TypedHandler extends TypedActionHandlerBase {
  private static final Logger LOG = Logger.getInstance(TypedHandler.class);
  private static final Set<Character> COMPLEX_CHARS = Set.of('\n', '\t', '(', ')', '<', '>', '[', ']', '{', '}', '"', '\'');

  public static boolean handleRParen(@NotNull Editor editor, @NotNull FileType fileType, char charTyped) {
    return new TypedBraceImpl().handleRParen(fileType, editor, charTyped);
  }

  public static void indentOpenedBrace(@NotNull Project project, @NotNull Editor editor) {
    new TypedBraceImpl().indentOpenedBrace(project, editor);
  }

  public static void indentBrace(@NotNull Project project, @NotNull Editor editor, char braceChar) {
    new TypedBraceImpl().indentBrace(project, editor, braceChar);
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

  private final TypedQuoteImpl quoteHandler = new TypedQuoteImpl();
  private final TypedBraceImpl braceHandler = new TypedBraceImpl();

  public TypedHandler(TypedActionHandler originalHandler) {
    super(originalHandler);
  }

  @Override
  public void beforeExecute(@NotNull Editor editor, char c, @NotNull DataContext context, @NotNull ActionPlan plan) {
    if (COMPLEX_CHARS.contains(c) || Character.isSurrogate(c)) return;

    for (TypedHandlerDelegate delegate : TypedHandlerDelegate.EP_NAME.getExtensionList()) {
      if (!delegate.isImmediatePaintingEnabled(editor, c, context)) {
        return;
      }
    }

    if (editor.isInsertMode()) {
      int offset = plan.getCaretOffset();
      plan.replace(offset, offset, String.valueOf(c));
    }

    super.beforeExecute(editor, c, context, plan);
  }

  @Override
  public void execute(@NotNull Editor originalEditor, char charTyped, @NotNull DataContext dataContext) {
    try (var ignored = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      doExecute(originalEditor, charTyped, dataContext);
    }
  }

  private void doExecute(@NotNull Editor originalEditor, char charTyped, @NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    PsiFile originalFile;

    if (project == null || (originalFile = PsiUtilBase.getPsiFileInEditor(originalEditor, project)) == null) {
      NewRdCompletionSupport.getInstance().noPsiAvailable(originalEditor);
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(originalEditor, charTyped, dataContext);
      }
      return;
    }

    if (!EditorModificationUtil.checkModificationAllowed(originalEditor)) return;

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    Document originalDocument = originalEditor.getDocument();
    fireNewTypingStarted(originalEditor, charTyped, dataContext);
    originalEditor.getCaretModel().runForEachCaret(caret -> {
      if (psiDocumentManager.isDocumentBlockedByPsi(originalDocument)) {
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(originalDocument); // to clean up after previous caret processing
      }

      Editor editor = injectedEditorIfCharTypedIsSignificant(charTyped, originalEditor, originalFile);
      PsiFile file = editor == originalEditor ? originalFile : Objects.requireNonNull(psiDocumentManager.getPsiFile(editor.getDocument()));

      try {
        if (caret == originalEditor.getCaretModel().getPrimaryCaret()) {
          setTypedEvent(editor, charTyped, TypedEvent.TypedHandlerPhase.CHECK_AUTO_POPUP);
          boolean handled = TypedCharImpl.callDelegates(TypedHandlerDelegate::checkAutoPopup, charTyped, project, editor, file);
          if (!handled) {
            setTypedEvent(editor, charTyped, TypedEvent.TypedHandlerPhase.AUTO_POPUP);
            autoPopupCompletion(editor, charTyped, project, file);
            autoPopupParameterInfo(editor, charTyped, project, file);
          }
        }
        if (editor instanceof EditorWindow && !((EditorWindow)editor).isValid()) {
          // delegate must have invalidated injected editor by calling commitDocument() or similar
          editor = injectedEditorIfCharTypedIsSignificant(charTyped, originalEditor, originalFile);
          file = editor == originalEditor ? originalFile : Objects.requireNonNull(psiDocumentManager.getPsiFile(editor.getDocument()));
        }
        if (!editor.isInsertMode()) {
          TypedCharImpl.type(originalEditor, project, charTyped);
          return;
        }

        setTypedEvent(editor, charTyped, TypedEvent.TypedHandlerPhase.BEFORE_SELECTION_REMOVED);
        if (TypedCharImpl.callDelegates(TypedHandlerDelegate::beforeSelectionRemoved, charTyped, project, editor, file)) {
          return;
        }

        var selectionModel = editor.getSelectionModel();
        if (selectionModel.hasSelection()) {
          int selectionLength = selectionModel.getSelectionEnd() - selectionModel.getSelectionStart();
          TypingEventsLogger.logSelectionDeleted(
            project,
            editor,
            file,
            selectionLength,
            TypingEventsLogger.SelectionDeleteAction.TYPING);
        }
        EditorModificationUtilEx.deleteSelectedText(editor);

        FileType fileType = TypedCharImpl.getFileType(file, editor);

        setTypedEvent(editor, charTyped, TypedEvent.TypedHandlerPhase.BEFORE_CHAR_TYPED);
        if (editor != originalEditor) {
          TypingEventsLogger.logTypedInInjected(project, originalFile, file);
        }
        TypedDelegateFunc func = (delegate, c1, p1, e1, f1) -> delegate.beforeCharTyped(c1, p1, e1, f1, fileType);
        if (TypedCharImpl.callDelegates(func, charTyped, project, editor, file)) {
          return;
        }

        if (')' == charTyped || ']' == charTyped || '}' == charTyped) {
          if (FileTypes.PLAIN_TEXT != fileType) {
            if (braceHandler.handleRParen(fileType, editor, charTyped)) return;
          }
        }
        else if ('"' == charTyped || '\'' == charTyped || '`' == charTyped) {
          if (quoteHandler.handleQuote(project, file, editor, charTyped)) return;
        }

        long modificationStampBeforeTyping = editor.getDocument().getModificationStamp();
        TypedCharImpl.type(originalEditor, project, charTyped);
        AutoHardWrapHandler.getInstance().wrapLineIfNecessary(originalEditor, dataContext, modificationStampBeforeTyping);

        if (editor.isDisposed()) { // can be that injected editor disappear
          return;
        }

        if (('(' == charTyped || '[' == charTyped || '{' == charTyped) &&
            CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
            fileType != FileTypes.PLAIN_TEXT) {
          braceHandler.handleAfterLParen(project, fileType, file, editor, charTyped);
        }
        else if ('}' == charTyped) {
          braceHandler.indentClosingBrace(project, editor);
        }
        else if (')' == charTyped) {
          braceHandler.indentClosingParenth(project, editor);
        }

        setTypedEvent(editor, charTyped, TypedEvent.TypedHandlerPhase.CHAR_TYPED);
        if (TypedCharImpl.callDelegates(TypedHandlerDelegate::charTyped, charTyped, project, editor, file)) {
          return;
        }

        if ('{' == charTyped) {
          braceHandler.indentOpenedBrace(project, editor);
        }
        else if ('(' == charTyped) {
          braceHandler.indentOpenedParenth(project, editor);
        }
      }
      finally {
        setTypedEvent(editor, charTyped, null);
      }
    });
  }

  private static void fireNewTypingStarted(@NotNull Editor originalEditor, char charTyped, @NotNull DataContext dataContext) {
    for (TypedHandlerDelegate delegate : TypedHandlerDelegate.EP_NAME.getExtensionList()) {
      delegate.newTypingStarted(charTyped, originalEditor, dataContext);
    }
  }

  private static void setTypedEvent(@NotNull Editor editor, char charTyped, @Nullable TypedEvent.TypedHandlerPhase phase) {
    TypedEvent event = phase == null ? null : new TypedEvent(charTyped, editor.getCaretModel().getOffset(), phase);
    editor.putUserData(CompletionPhase.AUTO_POPUP_TYPED_EVENT, event);
  }

  @FunctionalInterface
  interface TypedDelegateFunc {
    TypedHandlerDelegate.Result call(@NotNull TypedHandlerDelegate delegate,
                                     char charTyped,
                                     @NotNull Project project,
                                     @NotNull Editor editor,
                                     @NotNull PsiFile file);
  }

  private static void autoPopupParameterInfo(@NotNull Editor editor, char charTyped, @NotNull Project project, @NotNull PsiFile file) {
    if ((charTyped == '(' || charTyped == ',') && !isInsideStringLiteral(editor, file)) {
      AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
    }
  }

  /**
   * Note: If you want to implement autopopup for an arbitrary character, consider adding your own {@link TypedHandlerDelegate}
   * and implement {@link TypedHandlerDelegate#checkAutoPopup}
   */
  public static void autoPopupCompletion(@NotNull Editor editor, char charTyped, @NotNull Project project, @NotNull PsiFile file) {
    if (charTyped == '.' ||
        (charTyped == '/' && Boolean.TRUE.equals(editor.getUserData(AutoPopupController.ALLOW_AUTO_POPUP_FOR_SLASHES_IN_PATHS))) || // todo rewrite with TypedHandlerDelegate#checkAutoPopup
        isAutoPopup(editor, file, charTyped)
    ) {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
    }
  }

  public static void commitDocumentIfCurrentCaretIsNotTheFirstOne(@NotNull Editor editor, @NotNull Project project) {
    if (ContainerUtil.getFirstItem(editor.getCaretModel().getAllCarets()) != editor.getCaretModel().getCurrentCaret()) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    }
  }

  /**
   * @return true if auto-popup should be invoked according to deprecated {@link CompletionContributor#invokeAutoPopup)}.
   */
  private static boolean isAutoPopup(@NotNull Editor editor, @NotNull PsiFile file, char charTyped) {
    int offset = editor.getCaretModel().getOffset() - 1;
    if (offset < 0) {
      return false;
    }

    PsiElement element;
    Language language;
    if (file instanceof PsiFileWithOneLanguage) {
      language = file.getLanguage();

      // we know the language, so let's try to avoid inferring the element at caret
      // because there might be no contributors, so inferring element would be a waste of time.
      element = null;
    }
    else {
      element = file.findElementAt(offset);
      if (element == null) {
        return false;
      }
      language = RuntimeFlagsKt.isEditorLockFreeTypingEnabled()
                 ? file.getLanguage() // TODO: rework for lock-free typing, element.getLanguage() requires RA on EDT
                 : element.getLanguage();
    }

    List<CompletionContributor> contributors = CompletionContributor.forLanguageHonorDumbness(language, file.getProject());
    if (contributors.isEmpty()) {
      return false;
    }

    if (element == null) {
      // file is PsiFileWithOneLanguage, and there are contributors => we have to infer element.
      element = file.findElementAt(offset);
      if (element == null) {
        return false;
      }
    }

    PsiElement finalElement = element;
    CompletionContributor contributor = ContainerUtil.find(contributors, c -> c.invokeAutoPopup(finalElement, charTyped));
    if (contributor == null) {
      return false;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug(contributor + " requested completion autopopup when typing '" + charTyped + "'");
    }

    return true;
  }

  private static boolean isInsideStringLiteral(@NotNull Editor editor, @NotNull PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    boolean lockFreeTypingEnabled = RuntimeFlagsKt.isEditorLockFreeTypingEnabled();
    Language language;
    if (lockFreeTypingEnabled) {
      // TODO: rework for lock-free typing, element.getLanguage() requires RA on EDT
      language = file.getLanguage();
    }
    else {
      language = element.getLanguage();
    }
    ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    if (definition != null) {
      TokenSet stringLiteralElements = definition.getStringLiteralElements();
      ASTNode node = element.getNode();
      if (node == null) return false;
      IElementType elementType = node.getElementType();
      if (stringLiteralElements.contains(elementType)) {
        return true;
      }
      if (lockFreeTypingEnabled) {
        // TODO: rework for lock-free typing, element.getParent() requires RA on EDT
        return false;
      }
      PsiElement parent = element.getParent();
      if (parent != null) {
        ASTNode parentNode = parent.getNode();
        return parentNode != null && stringLiteralElements.contains(parentNode.getElementType());
      }
    }
    return false;
  }

  public static @NotNull Editor injectedEditorIfCharTypedIsSignificant(char charTyped,
                                                                       @NotNull Editor editor,
                                                                       @NotNull PsiFile oldFile) {
    return injectedEditorIfCharTypedIsSignificant((int)charTyped, editor, oldFile);
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
}
