package com.intellij.psi.impl.search;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.JspPsiUtil;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharSequenceSubSequence;

/**
 * @author yole
 */
public class JspIndexPatternBuilder implements IndexPatternBuilder {
  public Lexer getIndexingLexer(final PsiFile file) {
    if (JspPsiUtil.isInJspFile(file)) {
      EditorHighlighter highlighter;

      final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      EditorHighlighter cachedEditorHighlighter;
      boolean alreadyInitializedHighlighter = false;

      if (document instanceof DocumentImpl && 
          (cachedEditorHighlighter = ((DocumentImpl)document).getEditorHighlighterForCachesBuilding()) != null &&
          IdTableBuilding.checkCanUseCachedEditorHighlighter(file.getText(), cachedEditorHighlighter)
         ) {
        highlighter = cachedEditorHighlighter;
        alreadyInitializedHighlighter = true;
      } else {
        highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(file.getProject(),file.getVirtualFile());
      }
      return new LexerEditorHighlighterLexer(highlighter, alreadyInitializedHighlighter);
    }
    return null;
  }

  public TokenSet getCommentTokenSet(final PsiFile file) {
    final JspFile jspFile = JspPsiUtil.getJspFile(file);
    TokenSet commentTokens = TokenSet.orSet(JavaIndexPatternBuilder.XML_COMMENT_BIT_SET, StdTokenSets.COMMENT_BIT_SET);
    final ParserDefinition parserDefinition =
      LanguageParserDefinitions.INSTANCE.forLanguage(jspFile.getViewProvider().getTemplateDataLanguage());
    if (parserDefinition != null) {
      commentTokens = TokenSet.orSet(commentTokens, parserDefinition.getCommentTokens());
    }
    return commentTokens;
  }

  public int getCommentStartDelta(final IElementType tokenType) {
    return tokenType == JspTokenType.JSP_COMMENT ? "<%--".length() : 0;
  }

  public int getCommentEndDelta(final IElementType tokenType) {
    return tokenType == JspTokenType.JSP_COMMENT ? "--%>".length() : 0;
  }

  private static class LexerEditorHighlighterLexer extends LexerBase {
    HighlighterIterator iterator;
    CharSequence buffer;
    int start;
    int end;
    private final EditorHighlighter myHighlighter;
    private final boolean myAlreadyInitializedHighlighter;

    public LexerEditorHighlighterLexer(final EditorHighlighter highlighter, boolean alreadyInitializedHighlighter) {
      myHighlighter = highlighter;
      myAlreadyInitializedHighlighter = alreadyInitializedHighlighter;
    }

    public void start(CharSequence buffer, int startOffset, int endOffset, int state) {
      if (myAlreadyInitializedHighlighter) {
        this.buffer = buffer;
        start = startOffset;
        end = endOffset;
      } else {
        myHighlighter.setText(new CharSequenceSubSequence(this.buffer = buffer, start = startOffset, end = endOffset));
      }
      iterator = myHighlighter.createIterator(0);
    }

    public int getState() {
      return 0;
    }

    public IElementType getTokenType() {
      if (iterator.atEnd()) return null;
      return iterator.getTokenType();
    }

    public int getTokenStart() {
      return iterator.getStart();
    }

    public int getTokenEnd() {
      return iterator.getEnd();
    }

    public void advance() {
      iterator.advance();
    }

    public CharSequence getBufferSequence() {
      return buffer;
    }

    public int getBufferEnd() {
      return end;
    }
  }
}
