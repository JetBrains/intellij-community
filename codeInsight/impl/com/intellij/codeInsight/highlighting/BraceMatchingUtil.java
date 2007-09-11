
package com.intellij.codeInsight.highlighting;

import com.intellij.lang.BracePair;
import com.intellij.lang.Language;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.jsp.el.ELTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaDocElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.tree.jsp.IJspElementType;
import com.intellij.psi.tree.xml.IXmlLeafElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.xml.util.HtmlUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Stack;

public class BraceMatchingUtil {
  private static final int UNDEFINED_TOKEN_GROUP = -1;
  private static final int JAVA_TOKEN_GROUP = 0;
  private static final int XML_TAG_TOKEN_GROUP = 1;
  private static final int XML_VALUE_DELIMITER_GROUP = 2;
  private static final int JSP_TOKEN_GROUP = 3;
  private static final int PAIRED_TOKEN_GROUP = 4;
  private static final int DOC_TOKEN_GROUP = 5;

  private BraceMatchingUtil() {}

  public static boolean isAfterClassLikeIdentifierOrDot(final int offset, final Editor editor) {
    HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset);
    if (iterator.atEnd()) return false;
    if (iterator.getStart() > 0) iterator.retreat();
    final IElementType tokenType = iterator.getTokenType();
    if (tokenType == JavaTokenType.DOT) return true;
    if (tokenType == JavaTokenType.IDENTIFIER && iterator.getEnd() == offset) {
      final CharSequence chars = editor.getDocument().getCharsSequence();
      final char startChar = chars.charAt(iterator.getStart());
      if (!Character.isUpperCase(startChar)) return false;
      final CharSequence word = chars.subSequence(iterator.getStart(), iterator.getEnd());
      if (word.length() == 1) return true;
      for (int i = 1; i < word.length(); i++) {
        if (Character.isLowerCase(word.charAt(i))) return true;
      }
    }

    return false;
  }

  public static boolean isTokenInvalidInsideReference(final IElementType tokenType) {
    return tokenType == JavaTokenType.SEMICOLON ||
           tokenType == JavaTokenType.LBRACE ||
           tokenType == JavaTokenType.RBRACE;
  }

  public interface BraceMatcher {
    int getTokenGroup(IElementType tokenType);

    boolean isLBraceToken(HighlighterIterator iterator,CharSequence fileText, FileType fileType);
    boolean isRBraceToken(HighlighterIterator iterator,CharSequence fileText, FileType fileType);
    boolean isPairBraces(IElementType tokenType,IElementType tokenType2);
    boolean isStructuralBrace(HighlighterIterator iterator,CharSequence text, FileType fileType);
    IElementType getTokenType(char ch, HighlighterIterator iterator);
  }

  private static class PairedBraceMatcherAdapter implements BraceMatcher {
    private PairedBraceMatcher myMatcher;
    private Language myLanguage;

    public PairedBraceMatcherAdapter(final PairedBraceMatcher matcher, Language language) {
      myMatcher = matcher;
      myLanguage = language;
    }

    public int getTokenGroup(IElementType tokenType) {
      final BracePair[] pairs = myMatcher.getPairs();
      for (BracePair pair : pairs) {
        if (tokenType == pair.getLeftBraceType() || tokenType == pair.getRightBraceType()) return myLanguage.hashCode();
      }
      return UNDEFINED_TOKEN_GROUP;
    }

    public boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
      final IElementType tokenType = getToken(iterator);
      final BracePair[] pairs = myMatcher.getPairs();
      for (BracePair pair : pairs) {
        if (tokenType == pair.getLeftBraceType()) return true;
      }
      return false;
    }

    public boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
      final IElementType tokenType = getToken(iterator);
      final BracePair[] pairs = myMatcher.getPairs();
      for (BracePair pair : pairs) {
        if (tokenType == pair.getRightBraceType()) return true;
      }
      return false;
    }

    public boolean isPairBraces(IElementType tokenType, IElementType tokenType2) {
      final BracePair[] pairs = myMatcher.getPairs();
      for (BracePair pair : pairs) {
        if (tokenType == pair.getLeftBraceType() && tokenType2 == pair.getRightBraceType() ||
            tokenType == pair.getRightBraceType() && tokenType2 == pair.getLeftBraceType()) {
          return true;
        }
      }
      return false;
    }

    public boolean isStructuralBrace(HighlighterIterator iterator, CharSequence text, FileType fileType) {
      final IElementType tokenType = getToken(iterator);
      final BracePair[] pairs = myMatcher.getPairs();
      for (BracePair pair : pairs) {
        if (tokenType == pair.getRightBraceType() || tokenType == pair.getLeftBraceType()) return pair.isStructural();
      }
      return false;
    }

    public IElementType getTokenType(char ch, HighlighterIterator iterator) {
      if (iterator.atEnd()) return null;
      final IElementType tokenType = getToken(iterator);
      if (tokenType.getLanguage() != myLanguage) return null;
      final BracePair[] pairs = myMatcher.getPairs();

      for (final BracePair pair : pairs) {
        if (ch == pair.getRightBraceChar()) return pair.getRightBraceType();
        if (ch == pair.getLeftBraceChar()) return pair.getLeftBraceType();
      }
      return null;
    }
  }

  public static class DefaultBraceMatcher implements BraceMatcher {
    private static final BidirectionalMap<IElementType, IElementType> PAIRING_TOKENS = new BidirectionalMap<IElementType, IElementType>();
    static {
      PAIRING_TOKENS.put(JavaTokenType.LPARENTH, JavaTokenType.RPARENTH);
      PAIRING_TOKENS.put(JavaTokenType.LBRACE, JavaTokenType.RBRACE);
      PAIRING_TOKENS.put(JavaTokenType.LBRACKET, JavaTokenType.RBRACKET);
      PAIRING_TOKENS.put(XmlTokenType.XML_TAG_END, XmlTokenType.XML_START_TAG_START);
      PAIRING_TOKENS.put(XmlTokenType.XML_EMPTY_ELEMENT_END, XmlTokenType.XML_START_TAG_START);
      PAIRING_TOKENS.put(XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER, XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER);
      PAIRING_TOKENS.put(JspTokenType.JSP_SCRIPTLET_START, JspTokenType.JSP_SCRIPTLET_END);
      PAIRING_TOKENS.put(JspTokenType.JSP_EXPRESSION_START, JspTokenType.JSP_EXPRESSION_END);
      PAIRING_TOKENS.put(JspTokenType.JSP_DECLARATION_START, JspTokenType.JSP_DECLARATION_END);
      PAIRING_TOKENS.put(JspTokenType.JSP_DIRECTIVE_START, JspTokenType.JSP_DIRECTIVE_END);
      PAIRING_TOKENS.put(JavaDocTokenType.DOC_INLINE_TAG_START, JavaDocTokenType.DOC_INLINE_TAG_END);
      PAIRING_TOKENS.put(ELTokenType.JSP_EL_RBRACKET, ELTokenType.JSP_EL_LBRACKET);
      PAIRING_TOKENS.put(ELTokenType.JSP_EL_RPARENTH, ELTokenType.JSP_EL_LPARENTH);
    }

    public int getTokenGroup(IElementType tokenType) {
      if (tokenType instanceof IJavaElementType) {
        return JAVA_TOKEN_GROUP;
      }
      else if (tokenType instanceof IXmlLeafElementType) {
        return tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER || tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER
               ? XML_VALUE_DELIMITER_GROUP
               : XML_TAG_TOKEN_GROUP;
      }
      else if (tokenType instanceof IJspElementType) {
        return JSP_TOKEN_GROUP;
      }
      else if (tokenType instanceof IJavaDocElementType) {
        return DOC_TOKEN_GROUP;
      }
      else{
        return UNDEFINED_TOKEN_GROUP;
      }
    }

    public boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
      return isLBraceToken(getToken(iterator));
    }

    private static boolean isLBraceToken(final IElementType tokenType) {
      PairedBraceMatcher matcher = tokenType.getLanguage().getPairedBraceMatcher();
      if (matcher != null) {
        BracePair[] pairs = matcher.getPairs();
        for (BracePair pair : pairs) {
          if (pair.getLeftBraceType() == tokenType) return true;
        }
      }
      return tokenType == JavaTokenType.LPARENTH ||
             tokenType == JavaTokenType.LBRACE ||
             tokenType == JavaTokenType.LBRACKET ||
             tokenType == XmlTokenType.XML_START_TAG_START ||
             tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER ||
             tokenType == JspTokenType.JSP_SCRIPTLET_START ||
             tokenType == JspTokenType.JSP_EXPRESSION_START ||
             tokenType == JspTokenType.JSP_DECLARATION_START ||
             tokenType == JspTokenType.JSP_DIRECTIVE_START ||
             tokenType == ELTokenType.JSP_EL_LBRACKET ||
             tokenType == ELTokenType.JSP_EL_LPARENTH ||
             tokenType == JavaDocTokenType.DOC_INLINE_TAG_START;
    }

    public boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
      final IElementType tokenType = getToken(iterator);
      PairedBraceMatcher matcher = tokenType.getLanguage().getPairedBraceMatcher();
      if (matcher != null) {
        BracePair[] pairs = matcher.getPairs();
        for (BracePair pair : pairs) {
          if (pair.getRightBraceType() == tokenType) return true;
        }
      }

      if (tokenType == JavaTokenType.RPARENTH ||
          tokenType == JavaTokenType.RBRACE ||
          tokenType == JavaTokenType.RBRACKET ||
          tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END ||
          tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER ||
          tokenType == JspTokenType.JSP_SCRIPTLET_END ||
          tokenType == JspTokenType.JSP_EXPRESSION_END ||
          tokenType == JspTokenType.JSP_DECLARATION_END ||
          tokenType == JspTokenType.JSP_DIRECTIVE_END ||
          tokenType == ELTokenType.JSP_EL_RBRACKET ||
          tokenType == ELTokenType.JSP_EL_RPARENTH ||
          tokenType == JavaDocTokenType.DOC_INLINE_TAG_END) {
        return true;
      }
      else if (tokenType == XmlTokenType.XML_TAG_END) {
        final boolean result = findEndTagStart(iterator);

        if (fileType == StdFileTypes.HTML || fileType == StdFileTypes.JSP) {
          final String tagName = getTagName(fileText, iterator);

          if (tagName != null && HtmlUtil.isSingleHtmlTag(tagName)) {
            return !result;
          }
        }

        return result;
      }
      else {
        return false;
      }
    }

    public boolean isPairBraces(IElementType tokenType1, IElementType tokenType2) {
      if (tokenType2.equals(PAIRING_TOKENS.get(tokenType1))) return true;
      List<IElementType> keys = PAIRING_TOKENS.getKeysByValue(tokenType1);
      return keys != null && keys.contains(tokenType2);
    }

    public boolean isStructuralBrace(HighlighterIterator iterator,CharSequence text, FileType fileType) {
      IElementType tokenType = getToken(iterator);

      PairedBraceMatcher matcher = tokenType.getLanguage().getPairedBraceMatcher();
      if (matcher != null) {
        BracePair[] pairs = matcher.getPairs();
        for (BracePair pair : pairs) {
          if ((pair.getLeftBraceType() == tokenType || pair.getRightBraceType() == tokenType) &&
              pair.isStructural()) return true;
        }
      }
      if (fileType == StdFileTypes.JAVA) {
        return tokenType == JavaTokenType.RBRACE || tokenType == JavaTokenType.LBRACE;
      }
      else if (fileType == StdFileTypes.HTML ||
               fileType == StdFileTypes.XML ||
               fileType == StdFileTypes.XHTML
              ) {
        return tokenType == XmlTokenType.XML_START_TAG_START ||
               tokenType == XmlTokenType.XML_TAG_END ||
               tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END ||
               ( tokenType == XmlTokenType.XML_TAG_END &&
                 ( fileType == StdFileTypes.HTML ||
                   fileType == StdFileTypes.JSP
                 ) &&
                 isEndOfSingleHtmlTag(text, iterator)
               );
      }
      else {
        return (fileType == StdFileTypes.JSP || fileType == StdFileTypes.JSPX) && isJspJspxStructuralBrace(tokenType);
      }
    }

    private static boolean isJspJspxStructuralBrace(final IElementType tokenType) {
      return tokenType == JavaTokenType.LBRACE ||
             tokenType == JavaTokenType.RBRACE ||
             tokenType == XmlTokenType.XML_START_TAG_START ||
             tokenType == XmlTokenType.XML_TAG_END ||
             tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END;
    }

    public IElementType getTokenType(char ch, HighlighterIterator iterator) {
      IElementType tokenType = (!iterator.atEnd())?getToken(iterator) :null;

      if(tokenType == TokenType.WHITE_SPACE) {
        iterator.retreat();

        if (!iterator.atEnd()) {
          tokenType = getToken(iterator);
          iterator.advance();
        }
      }

      if(tokenType instanceof IJavaElementType) {
        if (ch == '}') return JavaTokenType.RBRACE;
        if (ch == '{') return JavaTokenType.LBRACE;
        if (ch == ']') return JavaTokenType.RBRACKET;
        if (ch == '[') return JavaTokenType.LBRACKET;
        if (ch == ')') return JavaTokenType.RPARENTH;
        if (ch == '(') return JavaTokenType.LPARENTH;
      } else if(tokenType instanceof IJspElementType) {
        if (ch == ']') return ELTokenType.JSP_EL_RBRACKET;
        if (ch == '[') return ELTokenType.JSP_EL_LBRACKET;
        if (ch == ')') return ELTokenType.JSP_EL_RPARENTH;
        if (ch == '(') return ELTokenType.JSP_EL_LPARENTH;
      }

      return null;  //TODO: add more here!
    }
  }

  public static class HtmlBraceMatcher extends DefaultBraceMatcher {
    private static BraceMatcher ourStyleBraceMatcher;
    private static BraceMatcher ourScriptBraceMatcher;

    public static void setStyleBraceMatcher(BraceMatcher braceMatcher) {
      ourStyleBraceMatcher = braceMatcher;
    }

    public static void setScriptBraceMatcher(BraceMatcher _scriptBraceMatcher) {
      ourScriptBraceMatcher = _scriptBraceMatcher;
    }

    public int getTokenGroup(IElementType tokenType) {
      int tokenGroup = super.getTokenGroup(tokenType);

      if(tokenGroup == UNDEFINED_TOKEN_GROUP && ourStyleBraceMatcher != null) {
        tokenGroup = ourStyleBraceMatcher.getTokenGroup(tokenType);
      }

      if(tokenGroup == UNDEFINED_TOKEN_GROUP && ourScriptBraceMatcher != null) {
        tokenGroup = ourScriptBraceMatcher.getTokenGroup(tokenType);
      }

      return tokenGroup;
    }

    public boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
      boolean islbrace = super.isLBraceToken(iterator, fileText, fileType);

      if (!islbrace && ourStyleBraceMatcher!=null) {
        islbrace = ourStyleBraceMatcher.isLBraceToken(iterator, fileText, fileType);
      }

      if (!islbrace && ourScriptBraceMatcher!=null) {
        islbrace = ourScriptBraceMatcher.isLBraceToken(iterator, fileText, fileType);
      }

      return islbrace;
    }

    public boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
      boolean rBraceToken = super.isRBraceToken(iterator, fileText, fileType);

      if (!rBraceToken && ourStyleBraceMatcher!=null) {
        rBraceToken = ourStyleBraceMatcher.isRBraceToken(iterator, fileText, fileType);
      }

      if (!rBraceToken && ourScriptBraceMatcher!=null) {
        rBraceToken = ourScriptBraceMatcher.isRBraceToken(iterator, fileText, fileType);
      }

      return rBraceToken;
    }

    public boolean isPairBraces(IElementType tokenType1, IElementType tokenType2) {
      boolean pairBraces = super.isPairBraces(tokenType1, tokenType2);

      if (!pairBraces && ourStyleBraceMatcher!=null) {
        pairBraces = ourStyleBraceMatcher.isPairBraces(tokenType1, tokenType2);
      }
      if (!pairBraces && ourScriptBraceMatcher!=null) {
        pairBraces = ourScriptBraceMatcher.isPairBraces(tokenType1, tokenType2);
      }

      return pairBraces;
    }

    public boolean isStructuralBrace(HighlighterIterator iterator, CharSequence text, FileType fileType) {
      boolean structuralBrace = super.isStructuralBrace(iterator, text, fileType);

      if (!structuralBrace && ourStyleBraceMatcher!=null) {
        structuralBrace = ourStyleBraceMatcher.isStructuralBrace(iterator, text, fileType);
      }
      if (!structuralBrace && ourScriptBraceMatcher!=null) {
        structuralBrace = ourScriptBraceMatcher.isStructuralBrace(iterator, text, fileType);
      }
      return structuralBrace;
    }

    public IElementType getTokenType(char ch, HighlighterIterator iterator) {
      IElementType pairedParenType = null;

      if (ourScriptBraceMatcher!=null) {
        pairedParenType = ourScriptBraceMatcher.getTokenType(ch, iterator);
      }

      if (pairedParenType == null && ourStyleBraceMatcher!=null) {
        pairedParenType = ourStyleBraceMatcher.getTokenType(ch, iterator);
      }

      if (pairedParenType == null) {
        pairedParenType = super.getTokenType(ch,iterator);
      }

      return pairedParenType;
    }
  }

  private static final HashMap<FileType,BraceMatcher> BRACE_MATCHERS = new HashMap<FileType, BraceMatcher>();

  public static void registerBraceMatcher(FileType fileType,BraceMatcher braceMatcher) {
    BRACE_MATCHERS.put(fileType, braceMatcher);
  }

  private static final Stack<IElementType> ourBraceStack = new Stack<IElementType>();
  private static final Stack<String> ourTagNameStack = new Stack<String>();

  private static class MatchBraceContext {
    CharSequence fileText;
    FileType fileType;
    HighlighterIterator iterator;
    boolean forward;

    IElementType brace1Token;
    int group;
    String brace1TagName;
    boolean isStrict;
    boolean isCaseSensitive;

    MatchBraceContext(CharSequence _fileText, FileType _fileType, HighlighterIterator _iterator, boolean _forward) {
      fileText = _fileText;
      fileType = _fileType;
      iterator = _iterator;
      forward = _forward;

      brace1Token = getToken(iterator);
      group = getTokenGroup(brace1Token, fileType);
      brace1TagName = getTagName(fileText, iterator);

      isStrict = isStrictTagMatching(fileType, group);
      isCaseSensitive = areTagsCaseSensitive(fileType, group);
    }

    MatchBraceContext(CharSequence _fileText, FileType _fileType, HighlighterIterator _iterator, boolean _forward,
                      boolean _strict) {
      this(_fileText, _fileType, _iterator, _forward);
      isStrict = _strict;
    }

    boolean doBraceMatch() {
      ourBraceStack.clear();
      ourTagNameStack.clear();
      ourBraceStack.push(brace1Token);
      if (isStrict){
        ourTagNameStack.push(brace1TagName);
      }
      boolean matched = false;
      while(true){
        if (!forward){
          iterator.retreat();
        }
        else{
          iterator.advance();
        }
        if (iterator.atEnd()) {
          break;
        }

        IElementType tokenType = getToken(iterator);

        if (getTokenGroup(tokenType, fileType) == group) {
          String tagName = getTagName(fileText, iterator);
          if (!isStrict && !Comparing.equal(brace1TagName, tagName, isCaseSensitive)) continue;
          if (forward ? isLBraceToken(iterator, fileText, fileType) : isRBraceToken(iterator, fileText, fileType)){
            ourBraceStack.push(tokenType);
            if (isStrict){
              ourTagNameStack.push(tagName);
            }
          }
          else if (forward ? isRBraceToken(iterator, fileText,fileType) : isLBraceToken(iterator, fileText, fileType)){
            IElementType topTokenType = ourBraceStack.pop();
            String topTagName = null;
            if (isStrict){
              topTagName = ourTagNameStack.pop();
            }

            if (!isPairBraces(topTokenType, tokenType, fileType)
              || isStrict && !Comparing.equal(topTagName, tagName, isCaseSensitive)
            ){
              matched = false;
              break;
            }

            if (ourBraceStack.size() == 0){
              matched = true;
              break;
            }
          }
        }
      }
      return matched;
    }
  }

  public static synchronized boolean matchBrace(CharSequence fileText, FileType fileType, HighlighterIterator iterator, 
                                                boolean forward) {
    return new MatchBraceContext(fileText, fileType, iterator, forward).doBraceMatch();
  }


  public static synchronized boolean matchBrace(CharSequence fileText, FileType fileType, HighlighterIterator iterator,
                                                boolean forward, boolean isStrict) {
    return new MatchBraceContext(fileText, fileType, iterator, forward, isStrict).doBraceMatch();
  }

  public static boolean findStructuralLeftBrace(FileType fileType, HighlighterIterator iterator, CharSequence fileText) {
    ourBraceStack.clear();
    ourTagNameStack.clear();

    while (!iterator.atEnd()) {
      if (isStructuralBraceToken(fileType, iterator,fileText)) {
        if (isRBraceToken(iterator, fileText, fileType)) {
          ourBraceStack.push(getToken(iterator));
          ourTagNameStack.push(getTagName(fileText, iterator));
        }
        if (isLBraceToken(iterator, fileText, fileType)) {
          if (ourBraceStack.size() == 0) return true;

          final int group = getTokenGroup(getToken(iterator), fileType);

          final IElementType topTokenType = ourBraceStack.pop();
          final IElementType tokenType = getToken(iterator);

          boolean isStrict = isStrictTagMatching(fileType, group);
          boolean isCaseSensitive = areTagsCaseSensitive(fileType, group);

          String topTagName = null;
          String tagName = null;
          if (isStrict){
            topTagName = ourTagNameStack.pop();
            tagName = getTagName(fileText, iterator);
          }

          if (!isPairBraces(topTokenType, tokenType, fileType)
            || isStrict && !Comparing.equal(topTagName, tagName, isCaseSensitive)) {
            return false;
          }
        }
      }

      iterator.retreat();
    }

    return false;
  }

  public static boolean isStructuralBraceToken(FileType fileType, HighlighterIterator iterator,CharSequence text) {
    BraceMatcher matcher = getBraceMatcher(fileType);
    return matcher != null && matcher.isStructuralBrace(iterator, text, fileType);
  }

  private static boolean isEndOfSingleHtmlTag(CharSequence text,HighlighterIterator iterator) {
    String tagName = getTagName(text,iterator);
    return tagName != null && HtmlUtil.isSingleHtmlTag(tagName);
  }

  public static boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType){
    final BraceMatcher braceMatcher = getBraceMatcher(fileType);

    return braceMatcher != null && braceMatcher.isLBraceToken(iterator, fileText, fileType);
  }

  public static boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType){
    final BraceMatcher braceMatcher = getBraceMatcher(fileType);

    return braceMatcher != null && braceMatcher.isRBraceToken(iterator, fileText, fileType);
  }

  public static boolean isPairBraces(IElementType tokenType1, IElementType tokenType2, FileType fileType){
    BraceMatcher matcher = getBraceMatcher(fileType);
    return matcher != null && matcher.isPairBraces(tokenType1, tokenType2);
  }

  private static int getTokenGroup(IElementType tokenType, FileType fileType){
    BraceMatcher matcher = getBraceMatcher(fileType);
    if (matcher!=null) return matcher.getTokenGroup(tokenType);
    return UNDEFINED_TOKEN_GROUP;
  }

  private static boolean isStrictTagMatching(FileType fileType, int tokenGroup) {
    switch(tokenGroup){
      case XML_TAG_TOKEN_GROUP:
        // Other xml languages may have nonbalanced tag names
        return fileType == StdFileTypes.XML ||
               fileType == StdFileTypes.XHTML ||
               fileType == StdFileTypes.JSPX;

      case JSP_TOKEN_GROUP:
        return true;

      default:
        return false;
    }
  }

  private static boolean areTagsCaseSensitive(FileType fileType, int tokenGroup) {
    switch(tokenGroup){
      case XML_TAG_TOKEN_GROUP:
        return fileType == StdFileTypes.XML;

      case JSP_TOKEN_GROUP:
        return true;

      default:
        return false;
    }
  }

  private static String getTagName(CharSequence fileText, HighlighterIterator iterator) {
    final IElementType tokenType = getToken(iterator);
    String name = null;
    if (tokenType == XmlTokenType.XML_START_TAG_START) {
      {
        boolean wasWhiteSpace = false;
        iterator.advance();
        IElementType tokenType1 = (!iterator.atEnd() ? getToken(iterator) :null);

        if (tokenType1 == JavaTokenType.WHITE_SPACE || tokenType1 == JspTokenType.JSP_WHITE_SPACE) {
          wasWhiteSpace = true;
          iterator.advance();
          tokenType1 = (!iterator.atEnd() ? getToken(iterator) :null);
        }

        if (tokenType1 == XmlTokenType.XML_TAG_NAME ||
            tokenType1 == XmlTokenType.XML_NAME
           ) {
          name = fileText.subSequence(iterator.getStart(), iterator.getEnd()).toString();
        }

        if (wasWhiteSpace) iterator.retreat();
        iterator.retreat();
      }
    }
    else if (tokenType == XmlTokenType.XML_TAG_END || tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END) {
      {
        int balance = 0;
        int count = 0;
        IElementType tokenType1 = getToken(iterator);
        while (balance >=0) {
          iterator.retreat();
          count++;
          if (iterator.atEnd()) break;
          tokenType1 = getToken(iterator);

          if(tokenType1 == XmlTokenType.XML_TAG_END || tokenType1 == XmlTokenType.XML_EMPTY_ELEMENT_END) balance++;
          else if(tokenType1 == XmlTokenType.XML_TAG_NAME)
            balance--;
        }
        if(tokenType1 == XmlTokenType.XML_TAG_NAME) name = fileText.subSequence(iterator.getStart(), iterator.getEnd()).toString();
        while (count-- > 0) iterator.advance();
      }
    }

    return name;
  }

  private static IElementType getToken(final HighlighterIterator iterator) {
      return iterator.getTokenType();
  }

  private static boolean findEndTagStart(HighlighterIterator iterator) {
    IElementType tokenType = getToken(iterator);
    int balance = 0;
    int count = 0;
    while(balance >= 0){
      iterator.retreat();
      count++;
      if (iterator.atEnd()) break;
      tokenType = getToken(iterator);
      if (tokenType == XmlTokenType.XML_TAG_END || tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END){
        balance++;
      }
      else if (tokenType == XmlTokenType.XML_END_TAG_START || tokenType == XmlTokenType.XML_START_TAG_START){
        balance--;
      }
    }
    while(count-- > 0) iterator.advance();
    return tokenType == XmlTokenType.XML_END_TAG_START;
  }

  // TODO: better name for this method
  public static int findLeftmostLParen(HighlighterIterator iterator, IElementType lparenTokenType, CharSequence fileText, FileType fileType){
    int lastLbraceOffset = -1;

    Stack<IElementType> braceStack = new Stack<IElementType>();
    for( ; !iterator.atEnd(); iterator.retreat()){
      final IElementType tokenType = getToken(iterator);

      if (isLBraceToken(iterator, fileText, fileType)){
        if (braceStack.size() > 0){
          IElementType topToken = braceStack.pop();
          if (!isPairBraces(tokenType, topToken, fileType)) {
            break; // unmatched braces
          }
        }
        else{
          if (tokenType == lparenTokenType){
            lastLbraceOffset = iterator.getStart();
          }
          else{
            break;
          }
        }
      }
      else if (isRBraceToken(iterator, fileText, fileType )){
        braceStack.push(getToken(iterator));
      }
    }

    return lastLbraceOffset;
  }

  // TODO: better name for this method
  public static int findRightmostRParen(HighlighterIterator iterator, IElementType rparenTokenType, CharSequence fileText, FileType fileType) {
    int lastRbraceOffset = -1;

    Stack<IElementType> braceStack = new Stack<IElementType>();
    for(; !iterator.atEnd(); iterator.advance()){
      final IElementType tokenType = getToken(iterator);

      if (isRBraceToken(iterator, fileText, fileType)){
        if (braceStack.size() > 0){
          IElementType topToken = braceStack.pop();
          if (!isPairBraces(tokenType, topToken, fileType)) {
            break; // unmatched braces
          }
        }
        else{
          if (tokenType == rparenTokenType){
            lastRbraceOffset = iterator.getStart();
          }
          else{
            break;
          }
        }
      }
      else if (isLBraceToken(iterator, fileText, fileType)){
        braceStack.push(getToken(iterator));
      }
    }

    return lastRbraceOffset;
  }

  public static BraceMatcher getBraceMatcher(FileType fileType) {
    BraceMatcher braceMatcher = BRACE_MATCHERS.get(fileType);
    if (braceMatcher==null) {
      if (fileType instanceof LanguageFileType) {
        final Language language = ((LanguageFileType)fileType).getLanguage();
        final PairedBraceMatcher matcher = language.getPairedBraceMatcher();
        if (matcher != null) {
          braceMatcher = new PairedBraceMatcherAdapter(matcher,language);
        }
      }
      if (braceMatcher == null) braceMatcher = getBraceMatcher(StdFileTypes.JAVA);
      BRACE_MATCHERS.put(fileType, braceMatcher);
    }
    return braceMatcher;
  }
}
