// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.TypedEvent;
import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.codeInsight.highlighting.NontrivialBraceMatcher;
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.statistic.collectors.fus.TypingEventsLogger;
import com.intellij.lang.*;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.ActionPlan;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.DefaultRawTypedHandler;
import com.intellij.openapi.editor.impl.TypedActionImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.KeyedExtensionCollector;
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
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TypedHandler extends TypedActionHandlerBase {
  private static final Set<Character> COMPLEX_CHARS =
    Set.of('\n', '\t', '(', ')', '<', '>', '[', ']', '{', '}', '"', '\'');

  private static final Logger LOG = Logger.getInstance(TypedHandler.class);

  private static final KeyedExtensionCollector<QuoteHandler, String> quoteHandlers = new KeyedExtensionCollector<>(QuoteHandlerEP.EP_NAME);

  public TypedHandler(TypedActionHandler originalHandler){
    super(originalHandler);
  }

  public static @Nullable QuoteHandler getQuoteHandler(@NotNull PsiFile file, @NotNull Editor editor) {
    FileType fileType = getFileType(file, editor);
    QuoteHandler quoteHandler = getQuoteHandlerForType(fileType);
    if (quoteHandler == null) {
      FileType fileFileType = file.getFileType();
      if (fileFileType != fileType) {
        quoteHandler = getQuoteHandlerForType(fileFileType);
      }
    }
    if (quoteHandler == null) {
      return getLanguageQuoteHandler(file.getViewProvider().getBaseLanguage());
    }
    return quoteHandler;
  }

  private static QuoteHandler getLanguageQuoteHandler(Language baseLanguage) {
    return LanguageQuoteHandling.INSTANCE.forLanguage(baseLanguage);
  }

  private static @NotNull FileType getFileType(@NotNull PsiFile file, @NotNull Editor editor) {
    FileType fileType = file.getFileType();
    Language language = PsiUtilBase.getLanguageInEditor(editor, file.getProject());
    if (language != null && language != PlainTextLanguage.INSTANCE) {
      LanguageFileType associatedFileType = language.getAssociatedFileType();
      if (associatedFileType != null) fileType = associatedFileType;
    }
    return fileType;
  }

  private static QuoteHandler getQuoteHandlerForType(@NotNull FileType fileType) {
    return ContainerUtil.getFirstItem(quoteHandlers.forKey(fileType.getName()));
  }

  /**
   * @deprecated use {@link QuoteHandlerEP}
   */
  @Deprecated
  public static void registerQuoteHandler(@NotNull FileType fileType, @NotNull QuoteHandler quoteHandler) {
    quoteHandlers.addExplicitExtension(fileType.getName(), quoteHandler);
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
  public void execute(final @NotNull Editor originalEditor, final char charTyped, final @NotNull DataContext dataContext) {
    try (var ignored = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      doExecute(originalEditor, charTyped, dataContext);
    }
  }

  private void doExecute(@NotNull Editor originalEditor, char charTyped, @NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final PsiFile originalFile;

    if (project == null || (originalFile = PsiUtilBase.getPsiFileInEditor(originalEditor, project)) == null) {
      if (myOriginalHandler != null){
        myOriginalHandler.execute(originalEditor, charTyped, dataContext);
      }
      return;
    }

    if (!EditorModificationUtil.checkModificationAllowed(originalEditor)) return;

    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    final Document originalDocument = originalEditor.getDocument();
    for (TypedHandlerDelegate delegate : TypedHandlerDelegate.EP_NAME.getExtensionList()) {
      delegate.newTypingStarted(charTyped, originalEditor, dataContext);
    }
    originalEditor.getCaretModel().runForEachCaret(caret -> {
      if (psiDocumentManager.isDocumentBlockedByPsi(originalDocument)) {
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(originalDocument); // to clean up after previous caret processing
      }

      Editor editor = injectedEditorIfCharTypedIsSignificant(charTyped, originalEditor, originalFile);
      PsiFile file = editor == originalEditor ? originalFile : Objects.requireNonNull(psiDocumentManager.getPsiFile(editor.getDocument()));

      try {
        if (caret == originalEditor.getCaretModel().getPrimaryCaret()) {
          setTypedEvent(editor, charTyped, TypedEvent.TypedHandlerPhase.CHECK_AUTO_POPUP);
          boolean handled = callDelegates(TypedHandlerDelegate::checkAutoPopup, charTyped, project, editor, file);
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
          type(originalEditor, project, charTyped);
          return;
        }

        setTypedEvent(editor, charTyped, TypedEvent.TypedHandlerPhase.BEFORE_SELECTION_REMOVED);
        if (callDelegates(TypedHandlerDelegate::beforeSelectionRemoved, charTyped, project, editor, file)) {
          return;
        }

        EditorModificationUtilEx.deleteSelectedText(editor);

        FileType fileType = getFileType(file, editor);

        setTypedEvent(editor, charTyped, TypedEvent.TypedHandlerPhase.BEFORE_CHAR_TYPED);
        if (editor != originalEditor) {
          TypingEventsLogger.logTypedInInjected(project, originalFile, file);
        }
        TypedDelegateFunc func = (delegate, c1, p1, e1, f1) -> delegate.beforeCharTyped(c1, p1, e1, f1, fileType);
        if (callDelegates(func, charTyped, project, editor, file)) {
          return;
        }

        if (')' == charTyped || ']' == charTyped || '}' == charTyped) {
          if (FileTypes.PLAIN_TEXT != fileType) {
            if (handleRParen(editor, fileType, charTyped)) return;
          }
        }
        else if ('"' == charTyped || '\'' == charTyped || '`' == charTyped/* || '/' == charTyped*/) {
          if (handleQuote(project, editor, charTyped, file)) return;
        }

        long modificationStampBeforeTyping = editor.getDocument().getModificationStamp();
        type(originalEditor, project, charTyped);
        AutoHardWrapHandler.getInstance().wrapLineIfNecessary(originalEditor, dataContext, modificationStampBeforeTyping);

        if (editor.isDisposed()) { // can be that injected editor disappear
          return;
        }

        if (('(' == charTyped || '[' == charTyped || '{' == charTyped) &&
            CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
            fileType != FileTypes.PLAIN_TEXT) {
          handleAfterLParen(project, editor, fileType, file, charTyped);
        }
        else if ('}' == charTyped) {
          indentClosingBrace(project, editor);
        }
        else if (')' == charTyped) {
          indentClosingParenth(project, editor);
        }

        setTypedEvent(editor, charTyped, TypedEvent.TypedHandlerPhase.CHAR_TYPED);
        if (callDelegates(TypedHandlerDelegate::charTyped, charTyped, project, editor, file)) {
          return;
        }

        if ('{' == charTyped) {
          indentOpenedBrace(project, editor);
        }
        else if ('(' == charTyped) {
          indentOpenedParenth(project, editor);
        }
      }
      finally {
        editor.putUserData(CompletionPhase.AUTO_POPUP_TYPED_EVENT, null);
      }
    });
  }

  private static void setTypedEvent(@NotNull Editor editor, char charTyped, @Nullable TypedEvent.TypedHandlerPhase phase) {
    if (phase == null) {
      editor.putUserData(CompletionPhase.AUTO_POPUP_TYPED_EVENT, null);
    }
    else {
      editor.putUserData(CompletionPhase.AUTO_POPUP_TYPED_EVENT, new TypedEvent(charTyped, editor.getCaretModel().getOffset(), phase));
    }
  }

  @FunctionalInterface
  interface TypedDelegateFunc {
    TypedHandlerDelegate.Result call(@NotNull TypedHandlerDelegate delegate, char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file);
  }

  // returns true if any delegate requested a STOP
  private static boolean callDelegates(@NotNull TypedDelegateFunc action, char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    boolean warned = false;
    for (TypedHandlerDelegate delegate : TypedHandlerDelegate.EP_NAME.getExtensionList()) {
      TypedHandlerDelegate.Result result = action.call(delegate, charTyped, project, editor, file);
      if (editor instanceof EditorWindow && !((EditorWindow)editor).isValid() && !warned) {
        LOG.warn(new IllegalStateException(delegate.getClass() + " has invalidated injected editor on typing char '"+charTyped+"'. Please don't call commitDocument() there or other destructive methods"));
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

  private static void type(@NotNull Editor editor, @NotNull Project project, char charTyped) {
    CommandProcessor.getInstance().setCurrentCommandName(EditorBundle.message("typing.in.editor.command.name"));
    EditorModificationUtilEx.insertStringAtCaret(editor, String.valueOf(charTyped), true, true);
    ((UndoManagerImpl)UndoManager.getInstance(project)).addDocumentAsAffected(editor.getDocument());
  }

  private static void autoPopupParameterInfo(@NotNull Editor editor, char charTyped, @NotNull Project project, @NotNull PsiFile file) {
    if ((charTyped == '(' || charTyped == ',') && !isInsideStringLiteral(editor, file)) {
      AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
    }
  }

  public static void autoPopupCompletion(@NotNull Editor editor, char charTyped, @NotNull Project project, @NotNull PsiFile file) {
    boolean allowSlashes = Boolean.TRUE.equals(editor.getUserData(AutoPopupController.ALLOW_AUTO_POPUP_FOR_SLASHES_IN_PATHS));
    if (charTyped == '.' || (allowSlashes && charTyped == '/') || isAutoPopup(editor, file, charTyped)) {
      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
    }
  }

  public static void commitDocumentIfCurrentCaretIsNotTheFirstOne(@NotNull Editor editor, @NotNull Project project) {
    if (ContainerUtil.getFirstItem(editor.getCaretModel().getAllCarets()) != editor.getCaretModel().getCurrentCaret()) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    }
  }

  private static boolean isAutoPopup(@NotNull Editor editor, @NotNull PsiFile file, char charTyped) {
    final int offset = editor.getCaretModel().getOffset() - 1;
    if (offset >= 0) {
      PsiElement element;
      Language language;
      if (file instanceof PsiFileWithOneLanguage) {
        element = null;
        language = file.getLanguage();
      }
      else {
        element = file.findElementAt(offset);
        if (element == null) {
          return false;
        }
        language = element.getLanguage();
      }

      for (CompletionContributor contributor : CompletionContributor.forLanguageHonorDumbness(language, file.getProject())) {
        if (element == null) {
          element = file.findElementAt(offset);
          if (element == null) {
            return false;
          }
        }
        if (contributor.invokeAutoPopup(element, charTyped)) {
          LOG.debug(contributor + " requested completion autopopup when typing '" + charTyped + "'");
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isInsideStringLiteral(@NotNull Editor editor, @NotNull PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    final ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(element.getLanguage());
    if (definition != null) {
      final TokenSet stringLiteralElements = definition.getStringLiteralElements();
      final ASTNode node = element.getNode();
      if (node == null) return false;
      final IElementType elementType = node.getElementType();
      if (stringLiteralElements.contains(elementType)) {
        return true;
      }
      PsiElement parent = element.getParent();
      if (parent != null) {
        ASTNode parentNode = parent.getNode();
        return parentNode != null && stringLiteralElements.contains(parentNode.getElementType());
      }
    }
    return false;
  }

  public static @NotNull Editor injectedEditorIfCharTypedIsSignificant(final char charTyped, @NotNull Editor editor, @NotNull PsiFile oldFile) {
    return injectedEditorIfCharTypedIsSignificant((int)charTyped, editor, oldFile);
  }

  static @NotNull Editor injectedEditorIfCharTypedIsSignificant(final int charTyped, @NotNull Editor editor, @NotNull PsiFile oldFile) {
    int offset = editor.getCaretModel().getOffset();
    // even for uncommitted document try to retrieve injected fragment that has been there recently
    // we are assuming here that when user is (even furiously) typing, injected language would not change
    // and thus we can use its lexer to insert closing braces etc
    List<DocumentWindow> injected = InjectedLanguageManager.getInstance(oldFile.getProject()).getCachedInjectedDocumentsInRange(oldFile, ProperTextRange.create(offset, offset));
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

  private static void handleAfterLParen(@NotNull Project project, @NotNull Editor editor, @NotNull FileType fileType, @NotNull PsiFile file, char lparenChar) {
    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
    boolean atEndOfDocument = offset == editor.getDocument().getTextLength();

    if (!atEndOfDocument) iterator.retreat();
    if (iterator.atEnd()) return;
    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
    if (iterator.atEnd()) return;
    IElementType braceTokenType = iterator.getTokenType();
    final CharSequence fileText = editor.getDocument().getCharsSequence();
    if (!braceMatcher.isLBraceToken(iterator, fileText, fileType)) return;

    if (!iterator.atEnd()) {
      iterator.advance();

      if (!iterator.atEnd() &&
          !BraceMatchingUtil.isPairedBracesAllowedBeforeTypeInFileType(braceTokenType, iterator.getTokenType(), fileType)) {
        return;
      }

      iterator.retreat();
    }

    int lparenOffset = BraceMatchingUtil.findLeftmostLParen(iterator, braceTokenType, fileText,fileType);
    if (lparenOffset < 0) lparenOffset = 0;

    iterator = editor.getHighlighter().createIterator(lparenOffset);
    boolean matched = BraceMatchingUtil.matchBrace(fileText, fileType, iterator, true, true);

    if (!matched) {
      String text = switch (lparenChar) {
        case '(' -> ")";
        case '[' -> "]";
        case '<' -> ">";
        case '{' -> "}";
        default -> throw new AssertionError("Unknown char '" + lparenChar + '\'');
      };
      if (callDelegates(TypedHandlerDelegate::beforeClosingParenInserted, text.charAt(0), project, editor, file)) {
        return;
      }
      editor.getDocument().insertString(offset, text);
      TabOutScopesTracker.getInstance().registerEmptyScope(editor, offset);
    }
  }

  public static boolean handleRParen(@NotNull Editor editor, @NotNull FileType fileType, char charTyped) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();

    if (offset == editor.getDocument().getTextLength()) return false;

    HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
    if (iterator.atEnd()) return false;

    if (iterator.getEnd() - iterator.getStart() != 1 || editor.getDocument().getCharsSequence().charAt(iterator.getStart()) != charTyped) {
      return false;
    }

    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
    CharSequence text = editor.getDocument().getCharsSequence();
    if (!braceMatcher.isRBraceToken(iterator, text, fileType)) {
      return false;
    }

    IElementType tokenType = iterator.getTokenType();

    iterator.retreat();

    IElementType lparenTokenType = braceMatcher.getOppositeBraceTokenType(tokenType);
    int lparenthOffset = BraceMatchingUtil.findLeftmostLParen(iterator, lparenTokenType, text, fileType);

    if (lparenthOffset < 0) {
      if (braceMatcher instanceof NontrivialBraceMatcher) {
        for(IElementType t:((NontrivialBraceMatcher)braceMatcher).getOppositeBraceTokenTypes(tokenType)) {
          if (t == lparenTokenType) continue;
          lparenthOffset = BraceMatchingUtil.findLeftmostLParen(
            iterator,
            t, text,
            fileType
          );
          if (lparenthOffset >= 0) break;
        }
      }
      if (lparenthOffset < 0) return false;
    }

    iterator = editor.getHighlighter().createIterator(lparenthOffset);
    boolean matched = BraceMatchingUtil.matchBrace(text, fileType, iterator, true, true);

    if (!matched) return false;

    EditorModificationUtil.moveCaretRelatively(editor, 1);
    return true;
  }

  @ApiStatus.Internal
  public static boolean handleQuote(@NotNull Project project, @NotNull Editor editor, char quote, @NotNull PsiFile file) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) return false;
    final QuoteHandler quoteHandler = getQuoteHandler(file, editor);
    if (quoteHandler == null) return false;

    int offset = editor.getCaretModel().getOffset();

    final Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    int length = document.getTextLength();
    if (isTypingEscapeQuote(editor, quoteHandler, offset)) return false;

    if (offset < length && chars.charAt(offset) == quote){
      if (isClosingQuote(editor, quoteHandler, offset)){
        EditorModificationUtil.moveCaretRelatively(editor, 1);
        return true;
      }
    }

    HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);

    if (!iterator.atEnd()){
      IElementType tokenType = iterator.getTokenType();
      if (quoteHandler instanceof JavaLikeQuoteHandler) {
        try {
          if (!((JavaLikeQuoteHandler)quoteHandler).isAppropriateElementTypeForLiteral(tokenType)) return false;
        }
        catch (AbstractMethodError incompatiblePluginErrorThatDoesNotInterestUs) {
          // ignore
        }
      }
    }

    type(editor, file.getProject(), quote);
    offset = editor.getCaretModel().getOffset();

    if (quoteHandler instanceof MultiCharQuoteHandler) {
      CharSequence closingQuote = getClosingQuote(editor, (MultiCharQuoteHandler)quoteHandler, offset);
      if (closingQuote != null && hasNonClosedLiterals(editor, quoteHandler, offset - 1)) {
        if (offset == document.getTextLength() ||
            !Character.isUnicodeIdentifierPart(document.getCharsSequence().charAt(offset))) { //any better heuristic or an API?
          TypedDelegateFunc func = (delegate, c1, p1, e1, f1) -> delegate.beforeClosingQuoteInserted(closingQuote, p1, e1, f1);
          if (!callDelegates(func, quote, project, editor, file)) {
            ((MultiCharQuoteHandler)quoteHandler).insertClosingQuote(editor, offset, file, closingQuote);
          }
          return true;
        }
      }
    }

    if (offset > 0 && isOpeningQuote(editor, quoteHandler, offset - 1) && hasNonClosedLiterals(editor, quoteHandler, offset - 1)) {
      if (offset == document.getTextLength() ||
          !Character.isUnicodeIdentifierPart(document.getCharsSequence().charAt(offset))) { //any better heuristic or an API?
        String quoteString = String.valueOf(quote);
        TypedDelegateFunc func = (delegate, c1, p1, e1, f1) -> delegate.beforeClosingQuoteInserted(quoteString, p1, e1, f1);
        if (!callDelegates(func, quote, project, editor, file)) {
          document.insertString(offset, quoteString);
          TabOutScopesTracker.getInstance().registerEmptyScope(editor, offset);
        }
      }
    }

    return true;
  }

  private static boolean isClosingQuote(@NotNull Editor editor, @NotNull QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = createIteratorAndCheckNotAtEnd(editor, offset);
    if (iterator == null) {
      return false;
    }

    return quoteHandler.isClosingQuote(iterator,offset);
  }

  private static @Nullable HighlighterIterator createIteratorAndCheckNotAtEnd(@NotNull Editor editor, int offset) {
    HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
    if (iterator.atEnd()) {
      LOG.error("Iterator " + iterator + " ended unexpectedly right after creation");
      return null;
    }
    return iterator;
  }

  private static @Nullable CharSequence getClosingQuote(@NotNull Editor editor, @NotNull MultiCharQuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = createIteratorAndCheckNotAtEnd(editor, offset);
    if (iterator == null) {
      return null;
    }

    return quoteHandler.getClosingQuote(iterator, offset);
  }

  private static boolean isOpeningQuote(@NotNull Editor editor, @NotNull QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = createIteratorAndCheckNotAtEnd(editor, offset);
    if (iterator == null) {
      return false;
    }

    return quoteHandler.isOpeningQuote(iterator, offset);
  }

  private static boolean hasNonClosedLiterals(@NotNull Editor editor, @NotNull QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = createIteratorAndCheckNotAtEnd(editor, offset);
    if (iterator == null) {
      return false;
    }

    return quoteHandler.hasNonClosedLiteral(editor, iterator, offset);
  }

  private static boolean isTypingEscapeQuote(@NotNull Editor editor, @NotNull QuoteHandler quoteHandler, int offset){
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    int offset1 = CharArrayUtil.shiftBackward(chars, offset - 1, "\\");
    int slashCount = offset - 1 - offset1;
    return slashCount % 2 != 0 && isInsideLiteral(editor, quoteHandler, offset);
  }

  private static boolean isInsideLiteral(@NotNull Editor editor, @NotNull QuoteHandler quoteHandler, int offset){
    if (offset == 0) return false;

    HighlighterIterator iterator = createIteratorAndCheckNotAtEnd(editor, offset - 1);
    if (iterator == null){
      return false;
    }

    return quoteHandler.isInsideLiteral(iterator);
  }

  private static void indentClosingBrace(@NotNull Project project, @NotNull Editor editor){
    indentBrace(project, editor, '}');
  }

  public static void indentOpenedBrace(@NotNull Project project, @NotNull Editor editor){
    indentBrace(project, editor, '{');
  }

  private static void indentOpenedParenth(@NotNull Project project, @NotNull Editor editor){
    indentBrace(project, editor, '(');
  }

  private static void indentClosingParenth(@NotNull Project project, @NotNull Editor editor){
    indentBrace(project, editor, ')');
  }

  public static void indentBrace(final @NotNull Project project, final @NotNull Editor editor, final char braceChar) {
    final int offset = editor.getCaretModel().getOffset() - 1;
    final Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    if (offset < 0 || chars.charAt(offset) != braceChar) return;

    int spaceStart = CharArrayUtil.shiftBackward(chars, offset - 1, " \t");
    if (spaceStart < 0 || chars.charAt(spaceStart) == '\n' || chars.charAt(spaceStart) == '\r'){
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file == null || !file.isWritable()) {
        return;
      }

      EditorHighlighter highlighter = editor.getHighlighter();
      HighlighterIterator iterator = highlighter.createIterator(offset);

      final FileType fileType = file.getFileType();
      BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
      boolean rBraceToken = braceMatcher.isRBraceToken(iterator, chars, fileType);
      final boolean isBrace = braceMatcher.isLBraceToken(iterator, chars, fileType) || rBraceToken;
      int lBraceOffset = -1;

      if (CodeInsightSettings.getInstance().REFORMAT_BLOCK_ON_RBRACE &&
          rBraceToken &&
          braceMatcher.isStructuralBrace(iterator, chars, fileType) && offset > 0) {
        lBraceOffset = BraceMatchingUtil.findLeftLParen(
          highlighter.createIterator(offset - 1),
          braceMatcher.getOppositeBraceTokenType(iterator.getTokenType()),
          editor.getDocument().getCharsSequence(),
          fileType
        );
      }
      if (isBrace) {
        DefaultRawTypedHandler handler = ((TypedActionImpl)TypedAction.getInstance()).getDefaultRawTypedHandler();
        handler.beginUndoablePostProcessing();

        final int finalLBraceOffset = lBraceOffset;
        ApplicationManager.getApplication().runWriteAction(() -> {
          final TypingActionsExtension extension = TypingActionsExtension.findForContext(project, editor);
          try{
            RangeMarker marker = document.createRangeMarker(offset, offset + 1);
            if (finalLBraceOffset != -1) {
              extension.format(project,
                               editor,
                               CodeInsightSettings.REFORMAT_BLOCK,
                               finalLBraceOffset,
                               offset,
                               0,
                               false,
                               false);
            }
            else {
              extension.format(project,
                               editor,
                               CodeInsightSettings.INDENT_EACH_LINE,
                               offset,
                               offset,
                               0,
                               false,
                               false);
            }
            editor.getCaretModel().moveToOffset(marker.getStartOffset() + 1);
            editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            editor.getSelectionModel().removeSelection();
            marker.dispose();
          }
          catch(IncorrectOperationException e){
            LOG.error(e);
          }
        });
      }
    }
  }
}

