package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.tree.IElementType;

public class BackspaceHandler extends EditorWriteActionHandler {
  private EditorActionHandler myOriginalHandler;

  public BackspaceHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void executeWriteAction(Editor editor, DataContext dataContext) {
    if (!handleBackspace(editor, dataContext)){
      myOriginalHandler.execute(editor, dataContext);
    }
  }

  private boolean handleBackspace(Editor editor, DataContext dataContext){
    Project project = (Project)DataManager.getInstance().getDataContext(editor.getContentComponent()).getData(DataConstants.PROJECT);
    if (project == null) return false;

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    if (file == null) return false;

    FileType fileType = file.getFileType();
    final TypedHandler.QuoteHandler quoteHandler = TypedHandler.getQuoteHandler(fileType);
    if (quoteHandler == null) return false;

    if (editor.getSelectionModel().hasSelection()) return false;

    int offset = editor.getCaretModel().getOffset() - 1;
    if (offset < 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    char c = chars.charAt(offset);

    boolean toDeleteGt = c =='<' &&
                        file instanceof PsiJavaFile &&
                        ((PsiJavaFile)file).getLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0
                        && BraceMatchingUtil.isAfterClassLikeIdentifierOrDot(offset, editor);

    HighlighterIterator hiterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    boolean wasClosingQuote = quoteHandler.isClosingQuote(hiterator, offset);

    myOriginalHandler.execute(editor, dataContext);

    if (offset >= editor.getDocument().getTextLength()) return true;
    chars = editor.getDocument().getCharsSequence();
    if (c == '(' || c == '[' || c == '{' || toDeleteGt){
      char c1 = chars.charAt(offset);
      if (c == '(' && c1 != ')') return true;
      if (c == '[' && c1 != ']') return true;
      if (c == '{' && c1 != '}') return true;

      if (c == '<') {
        if (c1 != '>') return true;
        handleLTDeletion(editor, offset);
        return true;
      }

      HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
      BraceMatchingUtil.BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);
      if (!braceMatcher.isLBraceToken(iterator, chars, fileType) &&
          !braceMatcher.isRBraceToken(iterator, chars, fileType)
          ) {
        return true;
      }

      final char closingBrace = c == '(' ? ')' : ']';
      int rparenOffset = BraceMatchingUtil.findRightmostRParen(iterator, braceMatcher.getTokenType(closingBrace, iterator),chars,fileType);
      if (rparenOffset >= 0){
        iterator = ((EditorEx)editor).getHighlighter().createIterator(rparenOffset);
        boolean matched = BraceMatchingUtil.matchBrace(chars, fileType, iterator, false);
        if (matched) return true;
      }

      editor.getDocument().deleteString(offset, offset + 1);
    }
    else if (c == '"' || c == '\''){
      char c1 = chars.charAt(offset);
      if (c1 != c) return true;
      if (wasClosingQuote) return true;

      HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
      if (!quoteHandler.isOpeningQuote(iterator,offset)) return true;

      editor.getDocument().deleteString(offset, offset + 1);
    }

    return true;
  }

  //need custom handler since cannot use brace matcher
  private static void handleLTDeletion(final Editor editor, final int offset) {
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
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

    if (balance < 0) {
      editor.getDocument().deleteString(offset, offset + 1);
    }
  }
}