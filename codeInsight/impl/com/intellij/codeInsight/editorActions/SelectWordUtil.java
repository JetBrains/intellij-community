package com.intellij.codeInsight.editorActions;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jsp.JspxFileViewProvider;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspCodeBlock;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.el.ELExpressionHolder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public class SelectWordUtil {
    
  private static ExtendWordSelectionHandler[] SELECTIONERS = new ExtendWordSelectionHandler[]{
    new LineCommentSelectioner(),
    new LiteralSelectioner(),
    new DocCommentSelectioner(),
    new ListSelectioner(),
    new CodeBlockOrInitializerSelectioner(),
    new FinallyBlockSelectioner(),
    new MethodOrClassSelectioner(),
    new FieldSelectioner(),
    new ReferenceSelectioner(),
    new DocTagSelectioner(),
    new IfStatementSelectioner(),
    new TypeCastSelectioner(),
    new JavaTokenSelectioner(),
    new WordSelectioner(),
    new StatementGroupSelectioner(),
    new CaseStatementsSelectioner(),
    new ScriptletSelectioner(),
    new PlainTextLineSelectioner(),
    new ELExpressionHolderSelectioner()
  };

  private static boolean ourExtensionsLoaded = false;

  public static void registerSelectioner(ExtendWordSelectionHandler selectioner) {
    SELECTIONERS = ArrayUtil.append(SELECTIONERS, selectioner);
  }

  static ExtendWordSelectionHandler[] getExtendWordSelectionHandlers() {
    if (!ourExtensionsLoaded) {
      ourExtensionsLoaded = true;
      for (ExtendWordSelectionHandler handler : Extensions.getExtensions(ExtendWordSelectionHandler.EP_NAME)) {
        registerSelectioner(handler);        
      }
    }
    return SELECTIONERS;
  }

  private static int findOpeningBrace(PsiElement[] children) {
    int start = 0;
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (child instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)child;

        if (token.getTokenType() == JavaTokenType.LBRACE) {
          int j = i + 1;

          while (children[j] instanceof PsiWhiteSpace) {
            j++;
          }

          start = children[j].getTextRange().getStartOffset();
        }
      }
    }
    return start;
  }

  private static int findClosingBrace(PsiElement[] children, int startOffset) {
    int end = children[children.length - 1].getTextRange().getEndOffset();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (child instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)child;

        if (token.getTokenType() == JavaTokenType.RBRACE) {
          int j = i - 1;

          while (children[j] instanceof PsiWhiteSpace && children[j].getTextRange().getStartOffset() > startOffset) {
            j--;
          }

          end = children[j].getTextRange().getEndOffset();
        }
      }
    }
    return end;
  }

  private static boolean isDocCommentElement(PsiElement element) {
    return element instanceof PsiDocTag;
  }

  private static class BasicSelectioner extends ExtendWordSelectionHandlerBase {

    public boolean canSelect(PsiElement e) {
      return canSelectBasic(e);
    }

    public static boolean canSelectBasic(final PsiElement e) {
      return
        !(e instanceof PsiWhiteSpace) &&
        !(e instanceof PsiComment) &&
        !(e instanceof PsiCodeBlock) &&
        !(e instanceof PsiArrayInitializerExpression) &&
        !(e instanceof PsiParameterList) &&
        !(e instanceof PsiExpressionList) &&
        !(e instanceof PsiBlockStatement) &&
        !(e instanceof PsiJavaCodeReferenceElement) &&
        !(e instanceof PsiJavaToken &&
        !(e instanceof PsiKeyword)) &&
        !(e instanceof XmlToken) &&
        !(e instanceof XmlElement) &&
        !isDocCommentElement(e);
    }
  }

  static class WordSelectioner extends AbstractWordSelectioner {
    public boolean canSelect(PsiElement e) {
      return BasicSelectioner.canSelectBasic(e) ||
             e instanceof PsiJavaToken && ((PsiJavaToken)e).getTokenType() == JavaTokenType.IDENTIFIER;
    }

  }

  public static void addWordSelection(boolean camel, CharSequence editorText, int cursorOffset, @NotNull List<TextRange> ranges) {
    TextRange camelRange = camel ? getCamelSelectionRange(editorText, cursorOffset) : null;
    if (camelRange != null) {
      ranges.add(camelRange);
    }

    TextRange range = getWordSelectionRange(editorText, cursorOffset);
    if (range != null && !range.equals(camelRange)) {
      ranges.add(range);
    }
  }

  private static TextRange getCamelSelectionRange(CharSequence editorText, int cursorOffset) {
    if (cursorOffset > 0 && !Character.isJavaIdentifierPart(editorText.charAt(cursorOffset)) &&
        Character.isJavaIdentifierPart(editorText.charAt(cursorOffset - 1))) {
      cursorOffset--;
    }

    if (Character.isJavaIdentifierPart(editorText.charAt(cursorOffset))) {
      int start = cursorOffset;
      int end = cursorOffset + 1;
      final int textLen = editorText.length();

      while (start > 0 && Character.isJavaIdentifierPart(editorText.charAt(start - 1))) {
        final char prevChar = editorText.charAt(start - 1);
        final char curChar = editorText.charAt(start);
        final char nextChar = start + 1 < textLen ? editorText.charAt(start + 1) : 0; // 0x00 is not lowercase.

        if (Character.isLowerCase(prevChar) && Character.isUpperCase(curChar) || prevChar == '_' && curChar != '_' ||
            Character.isUpperCase(prevChar) && Character.isUpperCase(curChar) && Character.isLowerCase(nextChar)) {
          break;
        }
        start--;
      }

      while (end < textLen && Character.isJavaIdentifierPart(editorText.charAt(end))) {
        final char prevChar = editorText.charAt(end - 1);
        final char curChar = editorText.charAt(end);
        final char nextChar = end + 1 < textLen ? editorText.charAt(end + 1) : 0; // 0x00 is not lowercase

        if (Character.isLowerCase(prevChar) && Character.isUpperCase(curChar) || prevChar != '_' && curChar == '_' ||
            Character.isUpperCase(prevChar) && Character.isUpperCase(curChar) && Character.isLowerCase(nextChar)) {
          break;
        }
        end++;
      }

      if (start + 1 < end) {
        return new TextRange(start, end);
      }
    }

    return null;
  }

  private static TextRange getWordSelectionRange(CharSequence editorText, int cursorOffset) {
    if (editorText.length() == 0) return null;
    if (cursorOffset > 0 && !Character.isJavaIdentifierPart(editorText.charAt(cursorOffset)) &&
        Character.isJavaIdentifierPart(editorText.charAt(cursorOffset - 1))) {
      cursorOffset--;
    }

    if (Character.isJavaIdentifierPart(editorText.charAt(cursorOffset))) {
      int start = cursorOffset;
      int end = cursorOffset;

      while (start > 0 && Character.isJavaIdentifierPart(editorText.charAt(start - 1))) {
        start--;
      }

      while (end < editorText.length() && Character.isJavaIdentifierPart(editorText.charAt(end))) {
        end++;
      }

      return new TextRange(start, end);
    }

    return null;
  }

  private static class LineCommentSelectioner extends WordSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiComment && !(e instanceof PsiDocComment);
    }

    public List<TextRange> select(PsiElement element, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = super.select(element, editorText, cursorOffset, editor);


      PsiElement firstComment = element;
      PsiElement e = element;

      while (e.getPrevSibling() != null) {
        if (e instanceof PsiComment) {
          firstComment = e;
        }
        else if (!(e instanceof PsiWhiteSpace)) {
          break;
        }
        e = e.getPrevSibling();
      }

      PsiElement lastComment = element;
      e = element;
      while (e.getNextSibling() != null) {
        if (e instanceof PsiComment) {
          lastComment = e;
        }
        else if (!(e instanceof PsiWhiteSpace)) {
          break;
        }
        e = e.getNextSibling();
      }


      result.addAll(expandToWholeLine(editorText, new TextRange(firstComment.getTextRange().getStartOffset(),
                                                                lastComment.getTextRange().getEndOffset())));

      return result;
    }
  }

  private static class DocCommentSelectioner extends LineCommentSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiDocComment;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

      PsiElement[] children = e.getChildren();

      int startOffset = e.getTextRange().getStartOffset();
      int endOffset = e.getTextRange().getEndOffset();

      for (PsiElement child : children) {
        if (child instanceof PsiDocToken) {
          PsiDocToken token = (PsiDocToken)child;

          if (token.getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
            char[] chars = token.getText().toCharArray();

            if (CharArrayUtil.shiftForward(chars, 0, " *\n\t\r") != chars.length) {
              break;
            }
          }
        }

        startOffset = child.getTextRange().getEndOffset();
      }

      for (PsiElement child : children) {
        if (child instanceof PsiDocToken) {
          PsiDocToken token = (PsiDocToken)child;

          if (token.getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
            char[] chars = token.getText().toCharArray();

            if (CharArrayUtil.shiftForward(chars, 0, " *\n\t\r") != chars.length) {
              endOffset = child.getTextRange().getEndOffset();
            }
          }
        }
      }

      startOffset = CharArrayUtil.shiftBackward(editorText, startOffset - 1, "* \t") + 1;

      result.add(new TextRange(startOffset, endOffset));

      return result;
    }
  }

  private static class ListSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiParameterList || e instanceof PsiExpressionList;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {

      PsiElement[] children = e.getChildren();

      int start = 0;
      int end = 0;

      for (PsiElement child : children) {
        if (child instanceof PsiJavaToken) {
          PsiJavaToken token = (PsiJavaToken)child;

          if (token.getTokenType() == JavaTokenType.LPARENTH) {
            start = token.getTextOffset() + 1;
          }
          if (token.getTokenType() == JavaTokenType.RPARENTH) {
            end = token.getTextOffset();
          }
        }
      }

      List<TextRange> result = new ArrayList<TextRange>();
      result.add(new TextRange(start, end));
      return result;
    }
  }

  private static class LiteralSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      PsiElement parent = e.getParent();
      return
        isStringLiteral(e) || isStringLiteral(parent);
    }

    private static boolean isStringLiteral(PsiElement element) {
      return element instanceof PsiLiteralExpression &&
             ((PsiLiteralExpression)element).getType().equalsToText("java.lang.String") && element.getText().startsWith("\"") && element.getText().endsWith("\"");
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

      TextRange range = e.getTextRange();
      final StringLiteralLexer lexer = new StringLiteralLexer('\"', JavaTokenType.STRING_LITERAL);
      lexer.start(editorText, range.getStartOffset(), range.getEndOffset(),0);
      
      while (lexer.getTokenType() != null) {
        if (lexer.getTokenStart() <= cursorOffset && cursorOffset < lexer.getTokenEnd()) {
          if (StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(lexer.getTokenType())) {
            result.add(new TextRange(lexer.getTokenStart(), lexer.getTokenEnd()));
          }
          else {
            TextRange word = getWordSelectionRange(editorText, cursorOffset);
            if (word != null) {
              result.add(new TextRange(Math.max(word.getStartOffset(), lexer.getTokenStart()),
                                       Math.min(word.getEndOffset(), lexer.getTokenEnd())));
            }
          }
          break;
        }
        lexer.advance();
      }

      result.add(new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1));

      return result;
    }
  }

  private static class FinallyBlockSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiKeyword && PsiKeyword.FINALLY.equals(e.getText());
    }


    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = new ArrayList<TextRange>();

      final PsiElement parent = e.getParent();
      if (parent instanceof PsiTryStatement) {
        final PsiTryStatement tryStatement = (PsiTryStatement)parent;
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock != null) {
          result.add(new TextRange(e.getTextRange().getStartOffset(), finallyBlock.getTextRange().getEndOffset()));
        }
      }

      return result;
    }
  }

  private static class CodeBlockOrInitializerSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiCodeBlock || e instanceof PsiArrayInitializerExpression;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = new ArrayList<TextRange>();

      PsiElement[] children = e.getChildren();

      int start = findOpeningBrace(children);
      int end = findClosingBrace(children, start);

      result.add(e.getTextRange());
      result.addAll(expandToWholeLine(editorText, new TextRange(start, end)));

      return result;
    }
  }

  /**
   *
   */

  private static class MethodOrClassSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiClass && !(e instanceof PsiTypeParameter) || e instanceof PsiMethod;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

      PsiElement firstChild = e.getFirstChild();
      PsiElement[] children = e.getChildren();

      if (firstChild instanceof PsiDocComment) {
        int i = 1;

        while (children[i] instanceof PsiWhiteSpace) {
          i++;
        }

        TextRange range = new TextRange(children[i].getTextRange().getStartOffset(), e.getTextRange().getEndOffset());
        result.addAll(expandToWholeLine(editorText, range));

        range = new TextRange(firstChild.getTextRange().getStartOffset(), firstChild.getTextRange().getEndOffset());
        result.addAll(expandToWholeLine(editorText, range));
      }
      else if (firstChild instanceof PsiComment) {
        int i = 1;

        while (children[i] instanceof PsiComment || children[i] instanceof PsiWhiteSpace) {
          i++;
        }
        PsiElement last = children[i - 1] instanceof PsiWhiteSpace ? children[i - 2] : children[i - 1];
        TextRange range = new TextRange(firstChild.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());
        if (range.contains(cursorOffset)) {
          result.addAll(expandToWholeLine(editorText, range));
        }

        range = new TextRange(children[i].getTextRange().getStartOffset(), e.getTextRange().getEndOffset());
        result.addAll(expandToWholeLine(editorText, range));
      }

      if (e instanceof PsiClass) {
        int start = findOpeningBrace(children);
        int end = findClosingBrace(children, start);

        result.addAll(expandToWholeLine(editorText, new TextRange(start, end)));
      }


      return result;
    }
  }

  private static class StatementGroupSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiStatement || e instanceof PsiComment && !(e instanceof PsiDocComment);
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = new ArrayList<TextRange>();

      PsiElement parent = e.getParent();

      if (!(parent instanceof PsiCodeBlock) && !(parent instanceof PsiBlockStatement) || parent instanceof JspCodeBlock) {
        return result;
      }


      PsiElement startElement = e;
      PsiElement endElement = e;


      while (startElement.getPrevSibling() != null) {
        PsiElement sibling = startElement.getPrevSibling();

        if (sibling instanceof PsiJavaToken) {
          PsiJavaToken token = (PsiJavaToken)sibling;
          if (token.getTokenType() == JavaTokenType.LBRACE) {
            break;
          }
        }

        if (sibling instanceof PsiWhiteSpace) {
          PsiWhiteSpace whiteSpace = (PsiWhiteSpace)sibling;

          String[] strings = LineTokenizer.tokenize(whiteSpace.getText().toCharArray(), false);
          if (strings.length > 2) {
            break;
          }
        }

        startElement = sibling;
      }

      while (startElement instanceof PsiWhiteSpace) {
        startElement = startElement.getNextSibling();
      }

      while (endElement.getNextSibling() != null) {
        PsiElement sibling = endElement.getNextSibling();

        if (sibling instanceof PsiJavaToken) {
          PsiJavaToken token = (PsiJavaToken)sibling;
          if (token.getTokenType() == JavaTokenType.RBRACE) {
            break;
          }
        }

        if (sibling instanceof PsiWhiteSpace) {
          PsiWhiteSpace whiteSpace = (PsiWhiteSpace)sibling;

          String[] strings = LineTokenizer.tokenize(whiteSpace.getText().toCharArray(), false);
          if (strings.length > 2) {
            break;
          }
        }

        endElement = sibling;
      }

      while (endElement instanceof PsiWhiteSpace) {
        endElement = endElement.getPrevSibling();
      }

      result.addAll(expandToWholeLine(editorText, new TextRange(startElement.getTextRange().getStartOffset(),
                                                                endElement.getTextRange().getEndOffset())));

      return result;
    }
  }

  private static class ReferenceSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiJavaCodeReferenceElement;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {

      PsiElement endElement = e;

      while (endElement instanceof PsiJavaCodeReferenceElement && endElement.getNextSibling() != null) {
        endElement = endElement.getNextSibling();
      }

      if (!(endElement instanceof PsiJavaCodeReferenceElement) &&
          !(endElement.getPrevSibling() instanceof PsiReferenceExpression && endElement instanceof PsiExpressionList)) {
        endElement = endElement.getPrevSibling();
      }

      PsiElement element = e;
      List<TextRange> result = new ArrayList<TextRange>();
      while (element instanceof PsiJavaCodeReferenceElement) {
        PsiElement firstChild = element.getFirstChild();

        PsiElement referenceName = ((PsiJavaCodeReferenceElement)element).getReferenceNameElement();
        if (referenceName != null) {
          result.addAll(expandToWholeLine(editorText, new TextRange(referenceName.getTextRange().getStartOffset(),
                                                                    endElement.getTextRange().getEndOffset())));
          if (endElement instanceof PsiJavaCodeReferenceElement) {
            final PsiElement endReferenceName = ((PsiJavaCodeReferenceElement)endElement).getReferenceNameElement();
            if (endReferenceName != null) {
              result.addAll(expandToWholeLine(editorText, new TextRange(referenceName.getTextRange().getStartOffset(),
                                                                        endReferenceName.getTextRange().getEndOffset())));
            }
          }

        }

        if (firstChild == null) break;
        element = firstChild;
      }

//      if (element instanceof PsiMethodCallExpression) {
      result.addAll(expandToWholeLine(editorText, new TextRange(element.getTextRange().getStartOffset(),
                                                                endElement.getTextRange().getEndOffset())));
//      }

      if (!(e.getParent() instanceof PsiJavaCodeReferenceElement)) {
        if (e.getNextSibling() instanceof PsiJavaToken ||
            e.getNextSibling() instanceof PsiWhiteSpace ||
            e.getNextSibling() instanceof PsiExpressionList) {
          result.addAll(super.select(e, editorText, cursorOffset, editor));
        }
      }

      return result;
    }
  }

  private static class DocTagSelectioner extends WordSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiDocTag;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

      TextRange range = e.getTextRange();

      int endOffset = range.getEndOffset();
      int startOffset = range.getStartOffset();

      PsiElement[] children = e.getChildren();

      for (int i = children.length - 1; i >= 0; i--) {
        PsiElement child = children[i];

        int childStartOffset = child.getTextRange().getStartOffset();

        if (childStartOffset <= cursorOffset) {
          break;
        }

        if (child instanceof PsiDocToken) {
          PsiDocToken token = (PsiDocToken)child;

          IElementType type = token.getTokenType();
          char[] chars = token.textToCharArray();
          int shift = CharArrayUtil.shiftForward(chars, 0, " \t\n\r");

          if (shift != chars.length && type != JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS) {
            break;
          }
        }
        else if (!(child instanceof PsiWhiteSpace)) {
          break;
        }

        endOffset = Math.min(childStartOffset, endOffset);
      }

      startOffset = CharArrayUtil.shiftBackward(editorText, startOffset - 1, "* \t") + 1;

      result.add(new TextRange(startOffset, endOffset));

      return result;
    }
  }

  private static class FieldSelectioner extends WordSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiField;
    }

    private static void addRangeElem(final List<TextRange> result,
                                     CharSequence editorText,
                                     final PsiElement first,
                                     final int end) {
      if (first != null) {
        result.addAll(expandToWholeLine(editorText,
                                        new TextRange(first.getTextRange().getStartOffset(), end)));
      }
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
      final PsiField field = (PsiField)e;
      final TextRange range = field.getTextRange();
      final PsiIdentifier first = field.getNameIdentifier();
      final TextRange firstRange = first.getTextRange();
      final PsiElement last = field.getInitializer();
      final int end = last == null ? firstRange.getEndOffset() : last.getTextRange().getEndOffset();
      addRangeElem(result, editorText, first, end);
      //addRangeElem (result, editorText, field, textLength, field.getTypeElement(), end);
      addRangeElem(result, editorText, field.getModifierList(), range.getEndOffset());
      //addRangeElem (result, editorText, field, textLength, field.getDocComment(), end);
      result.addAll(expandToWholeLine(editorText, range));
      return result;
    }
  }

  private static class JavaTokenSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiJavaToken && !(e instanceof PsiKeyword) && !(e.getParent()instanceof PsiCodeBlock);
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      PsiJavaToken token = (PsiJavaToken)e;

      if (token.getTokenType() != JavaTokenType.SEMICOLON && token.getTokenType() != JavaTokenType.LPARENTH) {
        return super.select(e, editorText, cursorOffset, editor);
      }
      else {
        return null;
      }
    }
  }

  static class IfStatementSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiIfStatement;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = new ArrayList<TextRange>();
      result.addAll(expandToWholeLine(editorText, e.getTextRange(), false));

      PsiIfStatement statement = (PsiIfStatement)e;

      final PsiKeyword elseKeyword = statement.getElseElement();
      if (elseKeyword != null) {
        result.addAll(expandToWholeLine(editorText,
                                        new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                      statement.getTextRange().getEndOffset()),
                                        false));

        final PsiStatement branch = statement.getElseBranch();
        if (branch instanceof PsiIfStatement) {
          PsiIfStatement elseIf = (PsiIfStatement)branch;
          final PsiKeyword element = elseIf.getElseElement();
          if (element != null) {
            result.addAll(expandToWholeLine(editorText,
                                            new TextRange(elseKeyword.getTextRange().getStartOffset(),
                                                          elseIf.getThenBranch().getTextRange().getEndOffset()),
                                            false));
          }
        }
      }

      return result;
    }
  }

  static class TypeCastSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiTypeCastExpression;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = new ArrayList<TextRange>();
      result.addAll(expandToWholeLine(editorText, e.getTextRange(), false));

      PsiTypeCastExpression expression = (PsiTypeCastExpression)e;
      PsiElement[] children = expression.getChildren();
      PsiElement lParen = null;
      PsiElement rParen = null;
      for (PsiElement child : children) {
        if (child instanceof PsiJavaToken) {
          PsiJavaToken token = (PsiJavaToken)child;
          if (token.getTokenType() == JavaTokenType.LPARENTH) lParen = token;
          if (token.getTokenType() == JavaTokenType.RPARENTH) rParen = token;
        }
      }

      if (lParen != null && rParen != null) {
        result.addAll(expandToWholeLine(editorText,
                                        new TextRange(lParen.getTextRange().getStartOffset(),
                                                      rParen.getTextRange().getEndOffset()),
                                        false));
      }

      return result;
    }
  }

  static class CaseStatementsSelectioner extends BasicSelectioner {
      public boolean canSelect(PsiElement e) {
        return  e.getParent() instanceof PsiCodeBlock && 
               e.getParent().getParent() instanceof PsiSwitchStatement;
      }

      public List<TextRange> select(PsiElement statement, CharSequence editorText, int cursorOffset, Editor editor) {
        List<TextRange> result = new ArrayList<TextRange>();
        PsiElement caseStart = statement;
        PsiElement caseEnd = statement;
        
        if (statement instanceof PsiSwitchLabelStatement ||
            statement instanceof PsiSwitchStatement) {
          return result;
        }

        PsiElement sibling = statement.getPrevSibling();
        while(sibling != null && !(sibling instanceof PsiSwitchLabelStatement)) {
          if (!(sibling instanceof PsiWhiteSpace)) caseStart = sibling;
          sibling = sibling.getPrevSibling();
        }
        
        sibling = statement.getNextSibling();
        while(sibling != null && !(sibling instanceof PsiSwitchLabelStatement)) {
          if (!(sibling instanceof PsiWhiteSpace) &&
              !(sibling instanceof PsiJavaToken) // end of switch
             ) {
            caseEnd = sibling;
          }
          sibling = sibling.getNextSibling();
        }

        final Document document = editor.getDocument();
        final int startOffset = document.getLineStartOffset(document.getLineNumber(caseStart.getTextOffset()));
        final int endOffset = document.getLineEndOffset(document.getLineNumber(caseEnd.getTextOffset() + caseEnd.getTextLength())) + 1;
        
        result.add(new TextRange(startOffset,endOffset));
        return result;
      }
    }

  static class ScriptletSelectioner extends BasicSelectioner {
    @Override
    public boolean canSelect(PsiElement e) {
      return PsiUtil.isInJspFile(e) && e.getLanguage() instanceof JavaLanguage;
    }

    @Override
    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> ranges = super.select(e, editorText, cursorOffset, editor);
      final JspFile psiFile = PsiUtil.getJspFile(e);
      if (e.getParent().getTextLength() == psiFile.getTextLength()) {
        final JspxFileViewProvider viewProvider = psiFile.getViewProvider();
        PsiElement elt = viewProvider.findElementAt(cursorOffset, viewProvider.getTemplateDataLanguage());
        ranges.add(elt.getTextRange());
      }
      return ranges;
    }
  }

  static class ELExpressionHolderSelectioner extends BasicSelectioner {
    @Override
    public boolean canSelect(PsiElement e) {
      return e instanceof ELExpressionHolder;
    }

    @Override
    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> ranges = super.select(e, editorText, cursorOffset, editor);
      ranges.add(e.getTextRange());
      return ranges;
    }
  }

  static class PlainTextLineSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiPlainText;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      return selectPlainTextLine(e, editorText, cursorOffset);
    }

    public static List<TextRange> selectPlainTextLine(final PsiElement e, final CharSequence editorText, final int cursorOffset) {
      int start = cursorOffset;
      while (start > 0 && editorText.charAt(start - 1) != '\n' && editorText.charAt(start - 1) != '\r') start--;

      int end = cursorOffset;
      while (end < editorText.length() && editorText.charAt(end) != '\n' && editorText.charAt(end) != '\r') end++;

      final TextRange range = new TextRange(start, end);
      if (!e.getParent().getTextRange().contains(range)) return null;
      List<TextRange> result = new ArrayList<TextRange>();
      result.add(range);
      return result;
    }
  }
}
