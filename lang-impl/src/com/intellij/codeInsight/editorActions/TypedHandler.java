package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class TypedHandler implements TypedActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.TypedHandler");

  private TypedActionHandler myOriginalHandler;

  private static final Map<FileType,QuoteHandler> quoteHandlers = new HashMap<FileType, QuoteHandler>();
  private static final Map<Class<? extends Language>, QuoteHandler> ourBaseLanguageQuoteHandlers = new HashMap<Class<? extends Language>, QuoteHandler>();

  public static @Nullable QuoteHandler getQuoteHandler(@NotNull PsiFile file) {
    QuoteHandler quoteHandler = getQuoteHandlerForType(file.getFileType());
    if (quoteHandler == null) {
      final Language baseLanguage = file.getViewProvider().getBaseLanguage();
      for (Map.Entry<Class<? extends Language>, QuoteHandler> entry : ourBaseLanguageQuoteHandlers.entrySet()) {
        if (entry.getKey().isInstance(baseLanguage)) {
          return entry.getValue();
        }
      }
    }
    return quoteHandler;
  }

  public static void registerBaseLanguageQuoteHandler(Class<? extends Language> languageClass, QuoteHandler quoteHandler) {
    ourBaseLanguageQuoteHandlers.put(languageClass, quoteHandler);
  }

  public static QuoteHandler getQuoteHandlerForType(final FileType fileType) {
    if (!quoteHandlers.containsKey(fileType)) {
      QuoteHandler handler = null;
      final QuoteHandlerEP[] handlerEPs = Extensions.getExtensions(QuoteHandlerEP.EP_NAME);
      for(QuoteHandlerEP ep: handlerEPs) {
        if (ep.fileType.equals(fileType.getName())) {
          handler = ep.getHandler();
          break;
        }
      }
      quoteHandlers.put(fileType, handler);
    }
    return quoteHandlers.get(fileType);
  }

  public static void registerQuoteHandler(FileType fileType, QuoteHandler quoteHandler) {
    quoteHandlers.put(fileType, quoteHandler);
  }

  public TypedHandler(TypedActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, char charTyped, DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null || editor.isColumnMode()){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, charTyped, dataContext);
      }
      return;
    }

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    if (file == null){
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, charTyped, dataContext);
      }
      return;
    }

    if (editor.isViewer()) return;

    if (!editor.getDocument().isWritable()) {
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(editor.getDocument(), project)) {
        return;
      }
    }

    Editor injectedEditor = injectedEditorIfCharTypedIsSignificant(charTyped, editor, file);
    if (injectedEditor != editor) {
      file = PsiDocumentManager.getInstance(project).getPsiFile(injectedEditor.getDocument());
      editor = injectedEditor;
    }

    final TypedHandlerDelegate[] delegates = Extensions.getExtensions(TypedHandlerDelegate.EP_NAME);
    AutoPopupController autoPopupController = AutoPopupController.getInstance(project);

    boolean handled = false;
    for(TypedHandlerDelegate delegate: delegates) {
      final TypedHandlerDelegate.Result result = delegate.checkAutoPopup(charTyped, project, editor, file);
      handled = result == TypedHandlerDelegate.Result.STOP;
      if (result != TypedHandlerDelegate.Result.CONTINUE) break;
    }

    if (!handled) {
      if (charTyped == '.') {
        autoPopupController.autoPopupMemberLookup(editor, null);
      }

      if (charTyped == '('){
        autoPopupController.autoPopupParameterInfo(editor, null);
      }
    }

    if (!editor.isInsertMode()){
      myOriginalHandler.execute(editor, charTyped, dataContext);
      return;
    }

    if (editor.getSelectionModel().hasSelection()){
      EditorModificationUtil.deleteSelectedText(editor);
    }

    final VirtualFile virtualFile = file.getVirtualFile();
    FileType fileType;

    if (virtualFile != null){
      fileType = virtualFile.getFileType();
    }
    else {
      fileType = file.getFileType();
    }

    for(TypedHandlerDelegate delegate: delegates) {
      final TypedHandlerDelegate.Result result = delegate.beforeCharTyped(charTyped, project, editor, file, fileType);
      if (result == TypedHandlerDelegate.Result.STOP) return;
      if (result == TypedHandlerDelegate.Result.DEFAULT) break;
    }

    if (')' == charTyped){
      if (handleRParen(editor, fileType, ')', '(')) return;
    }
    else if (']' == charTyped){
      if (handleRParen(editor, fileType, ']', '[')) return;
    }
    else if ('"' == charTyped || '\'' == charTyped){
      if (handleQuote(editor, fileType, charTyped, dataContext)) return;
    }

    myOriginalHandler.execute(editor, charTyped, dataContext);

    if ('(' == charTyped && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET){
      handleAfterLParen(editor, fileType, '(');
    }
    else if ('[' == charTyped && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET){
      handleAfterLParen(editor, fileType, '[');
    }
    else if ('}' == charTyped){
      indentClosingBrace(project, editor);
    }

    for(TypedHandlerDelegate delegate: delegates) {
      final TypedHandlerDelegate.Result result = delegate.charTyped(charTyped, project, editor, file);
      if (result == TypedHandlerDelegate.Result.STOP) return;
      if (result == TypedHandlerDelegate.Result.DEFAULT) break;
    }
    if ('{' == charTyped) {
      indentOpenedBrace(project, editor);
    }
  }

  static Editor injectedEditorIfCharTypedIsSignificant(final char charTyped, Editor editor, PsiFile oldFile) {
    boolean significant = charTyped == '"' ||
                          charTyped == '\'' ||
                          charTyped == '[' ||
                          charTyped == '(' ||
                          charTyped == ']' ||
                          charTyped == ')' ||
                          charTyped == '{' ||
                          charTyped == '}' ||
                          charTyped == '.';
    if (!significant) {
      return editor;
    }
    if (PsiDocumentManager.getInstance(oldFile.getProject()).isUncommited(editor.getDocument())) {
      return editor;
    }
    return InjectedLanguageUtil.getEditorForInjectedLanguage(editor, oldFile);
  }

  private static void handleAfterLParen(Editor editor, FileType fileType, char lparenChar){
    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    boolean atEndOfDocument = offset == editor.getDocument().getTextLength();

    if (!atEndOfDocument) iterator.retreat();
    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);
    IElementType braceTokenType = braceMatcher.getTokenType(lparenChar, iterator);
    if (iterator.atEnd() || iterator.getTokenType() != braceTokenType) return;

    if (!iterator.atEnd()) {
      iterator.advance();

      IElementType tokenType = !iterator.atEnd() ? iterator.getTokenType() : null;
      if (!BraceMatchingUtil.isPairedBracesAllowedBeforeTypeInFileType(braceTokenType, tokenType, fileType)) {
        return;
      }

      iterator.retreat();
    }

    int lparenOffset = BraceMatchingUtil.findLeftmostLParen(iterator, braceTokenType, editor.getDocument().getCharsSequence(),fileType);
    if (lparenOffset < 0) lparenOffset = 0;

    iterator = ((EditorEx)editor).getHighlighter().createIterator(lparenOffset);
    boolean matched = BraceMatchingUtil.matchBrace(editor.getDocument().getCharsSequence(), fileType, iterator, true);

    if (!matched){
      String text;
      if (lparenChar == '(') {
        text = ")";
      }
      else if (lparenChar == '[') {
        text = "]";
      }
      else if (lparenChar == '<') {
        text = ">";
      }
      else {
        LOG.assertTrue(false);

        return;
      }
      editor.getDocument().insertString(offset, text);
    }
  }

  private static boolean handleRParen(Editor editor, FileType fileType, char rightParen, char leftParen){
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();

    if (offset == editor.getDocument().getTextLength()) return false;

    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);
    if (iterator.atEnd() || braceMatcher.getTokenType(rightParen,iterator) != iterator.getTokenType()) {
      return false;
    }

    iterator.retreat();

    int lparenthOffset = BraceMatchingUtil.findLeftmostLParen(iterator, braceMatcher.getTokenType(leftParen, iterator),  editor.getDocument().getCharsSequence(),fileType);
    if (lparenthOffset < 0) return false;

    iterator = ((EditorEx) editor).getHighlighter().createIterator(lparenthOffset);
    boolean matched = BraceMatchingUtil.matchBrace(editor.getDocument().getCharsSequence(), fileType, iterator, true);

    if (!matched) return false;

    editor.getCaretModel().moveToOffset(offset + 1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return true;
  }

  private boolean handleQuote(Editor editor, FileType fileType, char quote, DataContext dataContext) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) return false;
    final QuoteHandler quoteHandler = getQuoteHandlerForType(fileType);
    if (quoteHandler == null) return false;

    int offset = editor.getCaretModel().getOffset();

    CharSequence chars = editor.getDocument().getCharsSequence();
    int length = editor.getDocument().getTextLength();
    if (isTypingEscapeQuote(editor, quoteHandler, offset)) return false;

    if (offset < length && chars.charAt(offset) == quote){
      if (isClosingQuote(editor, quoteHandler, offset)){
        editor.getCaretModel().moveToOffset(offset + 1);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        return true;
      }
    }

    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);

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

    myOriginalHandler.execute(editor, quote, dataContext);
    offset = editor.getCaretModel().getOffset();

    if (isOpeningQuote(editor, quoteHandler, offset - 1) &&
        hasNonClosedLiterals(editor, quoteHandler, offset - 1)) {
      editor.getDocument().insertString(offset, String.valueOf(quote));
    }

    return true;
  }

  private static boolean isClosingQuote(Editor editor, QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isClosingQuote(iterator,offset);
  }

  private static boolean isOpeningQuote(Editor editor, QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isOpeningQuote(iterator, offset);
  }

  private static boolean hasNonClosedLiterals(Editor editor, QuoteHandler quoteHandler, int offset) {
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()) {
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.hasNonClosedLiteral(editor, iterator, offset);
  }

  private static boolean isTypingEscapeQuote(Editor editor, QuoteHandler quoteHandler, int offset){
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    int offset1 = CharArrayUtil.shiftBackward(chars, offset - 1, "\\");
    int slashCount = (offset - 1) - offset1;
    return (slashCount % 2) != 0 && isInsideLiteral(editor, quoteHandler, offset);
  }

  private static boolean isInsideLiteral(Editor editor, QuoteHandler quoteHandler, int offset){
    if (offset == 0) return false;

    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset - 1);
    if (iterator.atEnd()){
      LOG.assertTrue(false);
      return false;
    }

    return quoteHandler.isInsideLiteral(iterator);
  }

  private static void indentClosingBrace(final Project project, final Editor editor){
    indentBrace(project, editor, '}');
  }

  public static void indentOpenedBrace(final Project project, final Editor editor){
    indentBrace(project, editor, '{');
  }

  private static void indentBrace(final Project project, final Editor editor, final char braceChar) {
    final int offset = editor.getCaretModel().getOffset() - 1;
    final Document document = editor.getDocument();
    CharSequence chars = document.getCharsSequence();
    if (offset < 0 || chars.charAt(offset) != braceChar) return;

    int spaceStart = CharArrayUtil.shiftBackward(chars, offset - 1, " \t");
    if (spaceStart < 0 || chars.charAt(spaceStart) == '\n' || chars.charAt(spaceStart) == '\r'){
      PsiDocumentManager.getInstance(project).commitDocument(document);

      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file == null || !file.isWritable()) return;
      PsiElement element = file.findElementAt(offset);
      if (element == null) return;

      BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(file.getFileType());
      EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
      HighlighterIterator iterator = highlighter.createIterator(offset);

      if (element.getNode() != null && braceMatcher.isStructuralBrace(iterator, chars, file.getFileType())) {
        final Runnable action = new Runnable() {
          public void run(){
            try{
              int newOffset = CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);
              editor.getCaretModel().moveToOffset(newOffset + 1);
              editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
              editor.getSelectionModel().removeSelection();
            }
            catch(IncorrectOperationException e){
              LOG.error(e);
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }
  }
}

