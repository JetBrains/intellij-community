package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.lang.BracePair;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageBraceMatching;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.xml.XMLLanguage;
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
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspSpiUtil;
import com.intellij.psi.jsp.el.ELExpressionHolder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class TypedHandler implements TypedActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.TypedHandler");

  private TypedActionHandler myOriginalHandler;

  public interface JavaLikeQuoteHandler extends QuoteHandler {
    TokenSet getConcatenatableStringTokenTypes();
    String getStringConcatenationOperatorRepresentation();

    TokenSet getStringTokenTypes();
    boolean isAppropriateElementTypeForLiteral(final @NotNull IElementType tokenType);
  }

  private static final Map<FileType,QuoteHandler> quoteHandlers = new HashMap<FileType, QuoteHandler>();

  public static @Nullable QuoteHandler getQuoteHandler(@NotNull PsiFile file) {
    QuoteHandler quoteHandler = getQuoteHandlerForType(file.getFileType());
    if (quoteHandler == null &&
        file.getViewProvider().getBaseLanguage() instanceof XMLLanguage
       ) {
      quoteHandler = getQuoteHandlerForType(StdFileTypes.XML);
    }
    return quoteHandler;
  }

  private static QuoteHandler getQuoteHandlerForType(final FileType fileType) {
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

  public static class SimpleTokenSetQuoteHandler implements QuoteHandler {
    protected final TokenSet myLiteralTokenSet;

    public SimpleTokenSetQuoteHandler(IElementType[] _literalTokens) {
      myLiteralTokenSet = TokenSet.create(_literalTokens);
    }

    public boolean isClosingQuote(HighlighterIterator iterator, int offset) {
      final IElementType tokenType = iterator.getTokenType();

      if (myLiteralTokenSet.contains(tokenType)){
        int start = iterator.getStart();
        int end = iterator.getEnd();
        return end - start >= 1 && offset == end - 1;
      }

      return false;
    }

    public boolean isOpeningQuote(HighlighterIterator iterator, int offset) {
      if (myLiteralTokenSet.contains(iterator.getTokenType())){
        int start = iterator.getStart();
        return offset == start;
      }

      return false;
    }

    public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
      try {
        Document doc = editor.getDocument();
        CharSequence chars = doc.getCharsSequence();
        int lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset));

        while (!iterator.atEnd() && iterator.getStart() < lineEnd) {
          IElementType tokenType = iterator.getTokenType();

          if (myLiteralTokenSet.contains(tokenType)) {
            if (iterator.getStart() >= iterator.getEnd() - 1 ||
                chars.charAt(iterator.getEnd() - 1) != '\"' && chars.charAt(iterator.getEnd() - 1) != '\'') {
              return true;
            }
          }
          iterator.advance();
        }
      }
      finally {
        while(iterator.atEnd() || iterator.getStart() != offset) iterator.retreat();
      }

      return false;
    }

    public boolean isInsideLiteral(HighlighterIterator iterator) {
      return myLiteralTokenSet.contains(iterator.getTokenType());
    }
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

    final Editor originalEditor = editor;
    final PsiFile originalFile = file;

    if (charTypedWeWantToShowSmartnessInInjectedLanguageWithoutPerformanceLoss(charTyped)) {
      Editor injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguage(editor, file);

      if (injectedEditor != editor) {
        file = PsiDocumentManager.getInstance(project).getPsiFile(injectedEditor.getDocument());
        editor = injectedEditor;
      }
    }

    final TypedHandlerDelegate[] delegates = Extensions.getExtensions(TypedHandlerDelegate.EP_NAME);
    AutoPopupController autoPopupController = AutoPopupController.getInstance(project);

    for(TypedHandlerDelegate delegate: delegates) {
      delegate.checkAutoPopup(charTyped, project, editor, file);
    }

    if (charTyped == '.') {
      autoPopupController.autoPopupMemberLookup(InjectedLanguageUtil.getEditorForInjectedLanguage(originalEditor, originalFile));
    }

    if (charTyped == '#') {
      autoPopupController.autoPopupMemberLookup(editor);
    }

    if (charTyped == '@' && file instanceof PsiJavaFile) {
      autoPopupController.autoPopupJavadocLookup(editor);
    }

    if (charTyped == '('){
      autoPopupController.autoPopupParameterInfo(editor, null);
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
    FileType originalFileType = null;

    if (virtualFile != null){
      originalFileType = fileType = virtualFile.getFileType();
    }
    else {
      fileType = file.getFileType();
    }

    for(TypedHandlerDelegate delegate: delegates) {
      if (delegate.beforeCharTyped(charTyped, project, editor, file, fileType)) {
        return;
      }
    }

    if ('>' == charTyped){
      if (file instanceof PsiJavaFile && !(file instanceof JspFile) &&
          CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
               ((PsiJavaFile)file).getLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0) {
        if (handleJavaGT(editor)) return;
      }
    }
    else if (')' == charTyped){
      if (handleRParen(editor, fileType, ')', '(')) return;
    }
    else if (']' == charTyped){
      if (handleRParen(editor, fileType, ']', '[')) return;
    }
    else if (';' == charTyped) {
      if (handleSemicolon(editor, fileType)) return;
    }
    else if ('"' == charTyped || '\'' == charTyped){
      if (handleQuote(editor, fileType, charTyped, dataContext)) return;
    } else if ('}' == charTyped) {
      if (originalFileType == StdFileTypes.JSPX || originalFileType == StdFileTypes.JSP) {
        if (handleELClosingBrace(editor, file, project)) return;
      } else if (originalFileType == StdFileTypes.JAVA) {
        if (handleJavaArrayInitializerRBrace(editor)) return;
      }
    }

    int offsetBefore = editor.getCaretModel().getOffset();

    //important to calculate before inserting charTyped
    boolean handleAfterJavaLT = '<' == charTyped &&
                                file instanceof PsiJavaFile &&
                                !(file instanceof JspFile) &&
                                CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
                                ((PsiJavaFile)file).getLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0 &&
                                BraceMatchingUtil.isAfterClassLikeIdentifierOrDot(offsetBefore, editor);

    myOriginalHandler.execute(editor, charTyped, dataContext);

    if (handleAfterJavaLT) {
      handleAfterJavaLT(editor);
    }
    else if ('(' == charTyped && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET){
      handleAfterLParen(editor, fileType, '(');
    }
    else if ('[' == charTyped && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET){
      handleAfterLParen(editor, fileType, '[');
    }
    else if ('}' == charTyped){
      indentClosingBrace(project, editor);
    }
    else if ('{' == charTyped){
      if (originalFileType == StdFileTypes.JSPX ||
          originalFileType == StdFileTypes.JSP
        ) {
        if(handleELOpeningBrace(editor, file, project)) return;
      } else if (originalFileType == StdFileTypes.JAVA) {
        if (handleJavaArrayInitializerLBrace(editor)) return;
      }

      indentOpenedBrace(project, editor);
    }
    else if ('=' == charTyped) {
      if (originalFileType == StdFileTypes.JSP) {
        handleJspEqual(project, editor);
      }
    }

    for(TypedHandlerDelegate delegate: delegates) {
      delegate.charTyped(charTyped, project, editor, file);
    }
  }

  static boolean charTypedWeWantToShowSmartnessInInjectedLanguageWithoutPerformanceLoss(final char charTyped) {
    return charTyped == '"' || charTyped == '\'' || charTyped == '[' || charTyped == '(' || charTyped == ']' || charTyped == ')' ||
      charTyped == '{' || charTyped == '}';
  }

  private boolean handleJavaArrayInitializerRBrace(final Editor editor) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.getStart() == 0 || iterator.getTokenType() != JavaTokenType.RBRACE) return false;
    iterator.retreat();
    if (!checkArrayInitializerLBrace(iterator)) return false;
    editor.getCaretModel().moveToOffset(offset + 1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return true;
  }

  private static boolean handleJavaArrayInitializerLBrace(final Editor editor) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset - 1);
    if (!checkArrayInitializerLBrace(iterator)) return false;
    editor.getDocument().insertString(offset, "}");
    return true;
  }

  private static boolean checkArrayInitializerLBrace(final HighlighterIterator iterator) {
    int lbraceCount = 0;
    while(iterator.getTokenType() == JavaTokenType.LBRACE) {
      lbraceCount++;
      iterator.retreat();
    }
    if (lbraceCount == 0) return false;
    if (iterator.getTokenType() == JavaTokenType.WHITE_SPACE) iterator.retreat();
    for(int i=0; i<lbraceCount; i++) {
      if (iterator.getTokenType() != JavaTokenType.RBRACKET) return false;
      iterator.retreat();
      if (iterator.getTokenType() != JavaTokenType.LBRACKET) return false;
      iterator.retreat();
    }
    return true;
  }

  //need custom handler, since brace matcher cannot be used
  private static boolean handleJavaGT(final Editor editor) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false;

    int offset = editor.getCaretModel().getOffset();

    if (offset == editor.getDocument().getTextLength()) return false;

    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.getTokenType() != JavaTokenType.GT) return false;
    while (!iterator.atEnd() && !BraceMatchingUtil.isTokenInvalidInsideReference(iterator.getTokenType())) {
      iterator.advance();
    }

    if (iterator.atEnd()) return false;
    if (BraceMatchingUtil.isTokenInvalidInsideReference(iterator.getTokenType())) iterator.retreat();

    int balance = 0;
    while (!iterator.atEnd() && balance >= 0) {
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType == JavaTokenType.LT) {
        balance--;
      }
      else if (tokenType == JavaTokenType.GT) {
        balance++;
      }
      else if (BraceMatchingUtil.isTokenInvalidInsideReference(tokenType)) {
        break;
      }

      iterator.retreat();
    }

    if (balance == 0) {
      editor.getCaretModel().moveToOffset(offset + 1);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return true;
    }

    return false;
  }

  //need custom handler, since brace matcher cannot be used
  private static void handleAfterJavaLT(final Editor editor) {
    if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return;

    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    while (iterator.getStart() > 0 && !BraceMatchingUtil.isTokenInvalidInsideReference(iterator.getTokenType())) {
      iterator.retreat();
    }

    if (BraceMatchingUtil.isTokenInvalidInsideReference(iterator.getTokenType())) iterator.advance();

    int balance = 0;
    while (!iterator.atEnd() && balance >= 0) {
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType == JavaTokenType.LT) {
        balance++;
      }
      else if (tokenType == JavaTokenType.GT) {
        balance--;
      }
      else if (BraceMatchingUtil.isTokenInvalidInsideReference(tokenType)) {
        break;
      }

      iterator.advance();
    }

    if (balance == 1) {
      editor.getDocument().insertString(offset, ">");
    }
  }

  private static boolean handleELOpeningBrace(final Editor editor, final PsiFile file, final Project project) {
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement elementAt = file.findElementAt(offset-1);

    // TODO: handle it with insertAfterLParen(...)
    if (!JspSpiUtil.isJavaContext(elementAt) &&
        ( elementAt.getText().equals("${") ||
          elementAt.getText().equals("#{")
        )
        ) {
      editor.getDocument().insertString(offset, "}");
      return true;
    }

    return false;
  }

  private static boolean handleELClosingBrace(final Editor editor, final PsiFile file, final Project project) {
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    final int offset = editor.getCaretModel().getOffset();
    PsiElement elementAt = file.findElementAt(offset);
    PsiElement parent = PsiTreeUtil.getParentOfType(elementAt,ELExpressionHolder.class);

    // TODO: handle it with insertAfterRParen(...)
    if (parent != null) {
      if (elementAt != null && elementAt.getText().equals("}")) {
        editor.getCaretModel().moveToOffset(offset + 1);
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        return true;
      }
    }

    return false;
  }

  private static void handleJspEqual(Project project, Editor editor) {
    final CharSequence chars = editor.getDocument().getCharsSequence();
    int current = editor.getCaretModel().getOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    JspFile file = PsiUtil.getJspFile(PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()));
    PsiElement element = file.findElementAt(current);
    if (element == null) {
      element = file.findElementAt(editor.getDocument().getTextLength() - 1);
      if (element == null) return;
    }
    if (current >= 3 && chars.charAt(current-3) == '<' && chars.charAt(current-2)=='%')  {
      while (element instanceof PsiWhiteSpace) {
        element = element.getNextSibling();
      }

      int ptr = current;
      while(ptr < chars.length() && Character.isWhitespace(chars.charAt(ptr))) ++ptr;

      if (ptr + 1 >= chars.length() || (chars.charAt(ptr) != '%' || chars.charAt(ptr+1) != '>') ) {
        editor.getDocument().insertString(current,"%>");
      }
    }
  }

  private static boolean handleSemicolon(Editor editor, FileType fileType) {
    if (fileType != StdFileTypes.JAVA) return false;
    int offset = editor.getCaretModel().getOffset();
    if (offset == editor.getDocument().getTextLength()) return false;

    char charAt = editor.getDocument().getCharsSequence().charAt(offset);
    if (charAt != ';') return false;

    editor.getCaretModel().moveToOffset(offset + 1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return true;
  }

  private static void handleAfterLParen(Editor editor, FileType fileType, char lparenChar){
    int offset = editor.getCaretModel().getOffset();
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    boolean atEndOfDocument = offset == editor.getDocument().getTextLength();

    if (!atEndOfDocument) iterator.retreat();
    BraceMatchingUtil.BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);
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
    BraceMatchingUtil.BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);
    if (iterator.atEnd() || braceMatcher.getTokenType(rightParen,iterator) != iterator.getTokenType()) {
      return false;
    }

    iterator.retreat();

    int lparenthOffset = BraceMatchingUtil.findLeftmostLParen(iterator, braceMatcher.getTokenType(leftParen, iterator),  editor.getDocument().getCharsSequence(),fileType);
    if (lparenthOffset < 0) return false;

    if (leftParen == '<' && !BraceMatchingUtil.isAfterClassLikeIdentifierOrDot(lparenthOffset, editor)) return false;

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
      if (fileType == StdFileTypes.JAVA || fileType == StdFileTypes.JSP){
        if (tokenType instanceof IJavaElementType){
          if (!JavaQuoteHandler.isAppropriateElementTypeForLiteralStatic(tokenType)) return false;
        }
      } else if (quoteHandler instanceof JavaLikeQuoteHandler) {
        try {
          if (!((JavaLikeQuoteHandler)quoteHandler).isAppropriateElementTypeForLiteral(tokenType)) return false;
        } catch (AbstractMethodError incompatiblePluginErrorThatDoesNotInterestUs) {}
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
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (!settings.AUTOINDENT_CLOSING_BRACE) return;

    indentBrace(project, editor, '}');
  }

  private static void indentOpenedBrace(final Project project, final Editor editor){
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
      IElementType braceTokenType = braceChar == '{' ? JavaTokenType.LBRACE:JavaTokenType.RBRACE;

      final Language language = element.getLanguage();
      final PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(language);
      
      if (matcher != null) {
        final BracePair[] pairs = matcher.getPairs();

        if (pairs != null) {
          for(BracePair pair:pairs) {
            if (pair.isStructural()) {
              if (pair.getLeftBraceChar() == braceChar) {
                braceTokenType = pair.getLeftBraceType(); break;
              } else if (pair.getRightBraceChar() == braceChar) {
                braceTokenType = pair.getRightBraceType(); break;
              }
            }
          }
        }
      }

      if (element.getNode() != null && element.getNode().getElementType() == braceTokenType){
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

