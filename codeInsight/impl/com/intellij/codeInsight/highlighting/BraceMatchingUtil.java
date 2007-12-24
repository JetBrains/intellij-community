
package com.intellij.codeInsight.highlighting;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageBraceMatching;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenTypeEx;
import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.xml.XmlTokenType;

import java.util.HashMap;
import java.util.Stack;

public class BraceMatchingUtil {
  public static final int UNDEFINED_TOKEN_GROUP = -1;
  public static final int XML_TAG_TOKEN_GROUP = 1;
  public static final int JSP_TOKEN_GROUP = 3;

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

  public static boolean isPairedBracesAllowedBeforeTypeInJava(final IElementType tokenType) {
    return TokenTypeEx.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(tokenType)
            || tokenType == JavaTokenType.SEMICOLON
            || tokenType == JavaTokenType.COMMA
            || tokenType == JavaTokenType.RPARENTH
            || tokenType == JavaTokenType.RBRACKET
            || tokenType == JavaTokenType.RBRACE;
  }

  public static boolean isPairedBracesAllowedBeforeTypeInFileType(final IElementType lbraceType, final IElementType tokenType, final FileType fileType) {
    if (tokenType instanceof IJavaElementType) return isPairedBracesAllowedBeforeTypeInJava(tokenType);
    try {
      return getBraceMatcher(fileType).isPairedBracesAllowedBeforeType(lbraceType, tokenType);
    } catch (AbstractMethodError incompatiblePluginThatWeDoNotCare) {}
    return true;
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

      brace1Token = iterator.getTokenType();
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

        IElementType tokenType = iterator.getTokenType();

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
          ourBraceStack.push(iterator.getTokenType());
          ourTagNameStack.push(getTagName(fileText, iterator));
        }
        if (isLBraceToken(iterator, fileText, fileType)) {
          if (ourBraceStack.size() == 0) return true;

          final int group = getTokenGroup(iterator.getTokenType(), fileType);

          final IElementType topTokenType = ourBraceStack.pop();
          final IElementType tokenType = iterator.getTokenType();

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

  public static String getTagName(CharSequence fileText, HighlighterIterator iterator) {
    final IElementType tokenType = iterator.getTokenType();
    String name = null;
    if (tokenType == XmlTokenType.XML_START_TAG_START) {
      {
        boolean wasWhiteSpace = false;
        iterator.advance();
        IElementType tokenType1 = (!iterator.atEnd() ? iterator.getTokenType() :null);

        if (tokenType1 == JavaTokenType.WHITE_SPACE || tokenType1 == JspTokenType.JSP_WHITE_SPACE) {
          wasWhiteSpace = true;
          iterator.advance();
          tokenType1 = (!iterator.atEnd() ? iterator.getTokenType() :null);
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
        IElementType tokenType1 = iterator.getTokenType();
        while (balance >=0) {
          iterator.retreat();
          count++;
          if (iterator.atEnd()) break;
          tokenType1 = iterator.getTokenType();

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

  // TODO: better name for this method
  public static int findLeftmostLParen(HighlighterIterator iterator, IElementType lparenTokenType, CharSequence fileText, FileType fileType){
    int lastLbraceOffset = -1;

    Stack<IElementType> braceStack = new Stack<IElementType>();
    for( ; !iterator.atEnd(); iterator.retreat()){
      final IElementType tokenType = iterator.getTokenType();

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
        braceStack.push(iterator.getTokenType());
      }
    }

    return lastLbraceOffset;
  }

  // TODO: better name for this method
  public static int findRightmostRParen(HighlighterIterator iterator, IElementType rparenTokenType, CharSequence fileText, FileType fileType) {
    int lastRbraceOffset = -1;

    Stack<IElementType> braceStack = new Stack<IElementType>();
    for(; !iterator.atEnd(); iterator.advance()){
      final IElementType tokenType = iterator.getTokenType();

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
        braceStack.push(iterator.getTokenType());
      }
    }

    return lastRbraceOffset;
  }

  public static BraceMatcher getBraceMatcher(FileType fileType) {
    BraceMatcher braceMatcher = BRACE_MATCHERS.get(fileType);
    if (braceMatcher==null) {
      if (fileType instanceof LanguageFileType) {
        final Language language = ((LanguageFileType)fileType).getLanguage();
        final PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(language);
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
