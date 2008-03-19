package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public class BackspaceHandler extends EditorWriteActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public BackspaceHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void executeWriteAction(Editor editor, DataContext dataContext) {
    if (!handleBackspace(editor, dataContext)){
      myOriginalHandler.execute(editor, dataContext);
    }
  }

  private boolean handleBackspace(Editor editor, DataContext dataContext){
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getContentComponent()));
    if (project == null) return false;

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    if (file == null) return false;

    if (editor.getSelectionModel().hasSelection()) return false;

    int offset = editor.getCaretModel().getOffset() - 1;
    if (offset < 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    char c = chars.charAt(offset);

    final Editor injectedEditor = TypedHandler.injectedEditorIfCharTypedIsSignificant(c, editor, file);
    if (injectedEditor != editor) {
      file = PsiDocumentManager.getInstance(project).getPsiFile(injectedEditor.getDocument());
      editor = injectedEditor;
      offset = editor.getCaretModel().getOffset() - 1;
      chars = editor.getDocument().getCharsSequence();
    }

    FileType fileType = file.getFileType();
    final QuoteHandler quoteHandler = TypedHandler.getQuoteHandler(file);
    if (quoteHandler == null) return false;

    final BackspaceHandlerDelegate[] delegates = Extensions.getExtensions(BackspaceHandlerDelegate.EP_NAME);
    for(BackspaceHandlerDelegate delegate: delegates) {
      delegate.beforeCharDeleted(c, file, editor);
    }

    HighlighterIterator hiterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    boolean wasClosingQuote = quoteHandler.isClosingQuote(hiterator, offset);

    myOriginalHandler.execute(editor, dataContext);

    if (offset >= editor.getDocument().getTextLength()) return true;

    for(BackspaceHandlerDelegate delegate: delegates) {
      if (delegate.charDeleted(c, file, editor)) {
        return true;
      }
    }


    chars = editor.getDocument().getCharsSequence();
    if (c == '(' || c == '[' || c == '{'){
      char c1 = chars.charAt(offset);
      if (c == '(' && c1 != ')') return true;
      if (c == '[' && c1 != ']') return true;
      if (c == '{' && c1 != '}') return true;

      HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
      BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);
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
}