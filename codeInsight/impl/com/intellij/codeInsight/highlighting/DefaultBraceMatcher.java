package com.intellij.codeInsight.highlighting;

import com.intellij.lang.BracePair;
import com.intellij.lang.LanguageBraceMatching;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.TokenTypeEx;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DefaultBraceMatcher implements BraceMatcher {
  private static final int JAVA_TOKEN_GROUP = 0;
  private static final int XML_TAG_TOKEN_GROUP = 1;
  private static final int XML_VALUE_DELIMITER_GROUP = 2;
  private static final int JSP_TOKEN_GROUP = 3;
  private static final int PAIRED_TOKEN_GROUP = 4;
  private static final int DOC_TOKEN_GROUP = 5;

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
      return BraceMatchingUtil.UNDEFINED_TOKEN_GROUP;
    }
  }

  public boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    return isLBraceToken(iterator.getTokenType());
  }

  private static boolean isLBraceToken(final IElementType tokenType) {
    PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(tokenType.getLanguage());
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
    final IElementType tokenType = iterator.getTokenType();
    PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(tokenType.getLanguage());
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
    IElementType tokenType = iterator.getTokenType();

    PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(tokenType.getLanguage());
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
    IElementType tokenType = (!iterator.atEnd())? iterator.getTokenType() :null;

    if(tokenType == TokenType.WHITE_SPACE) {
      iterator.retreat();

      if (!iterator.atEnd()) {
        tokenType = iterator.getTokenType();
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

  public boolean isPairedBracesAllowedBeforeType(@NotNull final IElementType lbraceType, @Nullable final IElementType contextType) {
    if (contextType instanceof IJavaElementType) return isPairedBracesAllowedBeforeTypeInJava(contextType);
    return true;
  }

  public boolean isStrictTagMatching(final FileType fileType, final int group) {
    switch(group){
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

  public boolean areTagsCaseSensitive(final FileType fileType, final int tokenGroup) {
    switch(tokenGroup){
      case XML_TAG_TOKEN_GROUP:
        return fileType == StdFileTypes.XML;

      case JSP_TOKEN_GROUP:
        return true;

      default:
        return false;
    }
  }

  private static boolean findEndTagStart(HighlighterIterator iterator) {
    IElementType tokenType = iterator.getTokenType();
    int balance = 0;
    int count = 0;
    while(balance >= 0){
      iterator.retreat();
      count++;
      if (iterator.atEnd()) break;
      tokenType = iterator.getTokenType();
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

  private boolean isEndOfSingleHtmlTag(CharSequence text,HighlighterIterator iterator) {
    String tagName = getTagName(text,iterator);
    return tagName != null && HtmlUtil.isSingleHtmlTag(tagName);
  }

  public static boolean isPairedBracesAllowedBeforeTypeInJava(final IElementType tokenType) {
    return TokenTypeEx.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(tokenType)
            || tokenType == JavaTokenType.SEMICOLON
            || tokenType == JavaTokenType.COMMA
            || tokenType == JavaTokenType.RPARENTH
            || tokenType == JavaTokenType.RBRACKET
            || tokenType == JavaTokenType.RBRACE;
  }

  public String getTagName(CharSequence fileText, HighlighterIterator iterator) {
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
}
