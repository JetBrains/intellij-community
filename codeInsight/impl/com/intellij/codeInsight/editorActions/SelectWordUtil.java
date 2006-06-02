package com.intellij.codeInsight.editorActions;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jsp.JspxFileViewProvider;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspCodeBlock;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public class SelectWordUtil {
    
  static Selectioner[] SELECTIONERS = new Selectioner[]{
    new LineCommentSelectioner(),
    new LiteralSelectioner(),
    new DocCommentSelectioner(),
    new ListSelectioner(),
    new CodeBlockOrInitializerSelectioner(),
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
    new HtmlSelectioner(),
    new XmlTagSelectioner(),
    new XmlCDATAContentSelectioner(),
    new DtdSelectioner(),
    new XmlElementSelectioner(),
    new XmlTokenSelectioner(),
    new ScriptletSelectioner(),
    new PlainTextLineSelectioner()
  };

  public static void registerSelectioner(Selectioner selectioner) {
    SELECTIONERS = ArrayUtil.append(SELECTIONERS, selectioner);
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

  private static int findClosingBrace(PsiElement[] children) {
    int end = children[children.length - 1].getTextRange().getEndOffset();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (child instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)child;

        if (token.getTokenType() == JavaTokenType.RBRACE) {
          int j = i - 1;

          while (children[j] instanceof PsiWhiteSpace) {
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

  private static List<TextRange> expandToWholeLine(CharSequence text, TextRange range, boolean isSymmetric) {
    int textLength = text.length();
    List<TextRange> result = new ArrayList<TextRange>();

    if (range == null) {
      return result;
    }

    boolean hasNewLines = false;

    for (int i = range.getStartOffset(); i < range.getEndOffset(); i++) {
      char c = text.charAt(i);

      if (c == '\r' || c == '\n') {
        hasNewLines = true;
        break;
      }
    }

    if (!hasNewLines) {
      result.add(range);
    }


    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();
    int index1 = CharArrayUtil.shiftBackward(text, startOffset - 1, " \t");
    if (endOffset > startOffset && text.charAt(endOffset - 1) == '\n' || text.charAt(endOffset - 1) == '\r') {
      endOffset--;
    }
    int index2 = Math.min(textLength, CharArrayUtil.shiftForward(text, endOffset, " \t"));

    if (index1 < 0
        || text.charAt(index1) == '\n'
        || text.charAt(index1) == '\r'
        || index2 == textLength
        || text.charAt(index2) == '\n'
        || text.charAt(index2) == '\r') {

      if (!isSymmetric) {
        if (index1 < 0 || text.charAt(index1) == '\n' || text.charAt(index1) == '\r') {
          startOffset = index1 + 1;
        }

        if (index2 == textLength || text.charAt(index2) == '\n' || text.charAt(index2) == '\r') {
          endOffset = index2;
          if (endOffset < textLength) {
            endOffset++;
            if (endOffset < textLength && text.charAt(endOffset - 1) == '\r' && text.charAt(endOffset) == '\n') {
              endOffset++;
            }
          }
        }

        result.add(new TextRange(startOffset, endOffset));
      }
      else {
        if ((index1 < 0 || text.charAt(index1) == '\n' || text.charAt(index1) == '\r') &&
            (index2 == textLength || text.charAt(index2) == '\n' || text.charAt(index2) == '\r')) {
          startOffset = index1 + 1;
          endOffset = index2;
          if (endOffset < textLength) {
            endOffset++;
            if (endOffset < textLength && text.charAt(endOffset - 1) == '\r' && text.charAt(endOffset) == '\n') {
              endOffset++;
            }
          }
          result.add(new TextRange(startOffset, endOffset));
        }
        else {
          result.add(range);
        }
      }
    }
    else {
      result.add(range);
    }

    return result;
  }

  private static List<TextRange> expandToWholeLine(CharSequence text, TextRange range) {
    return expandToWholeLine(text, range, true);
  }

  public static interface Selectioner {
    boolean canSelect(PsiElement e);

    List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor);
  }

  private static class BasicSelectioner implements Selectioner {
    protected boolean canSelectXml(PsiElement e) {
      return !(e instanceof XmlToken) && !(e instanceof XmlElement);
    }

    public boolean canSelect(PsiElement e) {
      return
        !(e instanceof PsiWhiteSpace) &&
        !(e instanceof PsiComment) &&
        !(e instanceof PsiCodeBlock) &&
        !(e instanceof PsiArrayInitializerExpression) &&
        !(e instanceof PsiParameterList) &&
        !(e instanceof PsiExpressionList) &&
        !(e instanceof PsiBlockStatement) &&
        !(e instanceof PsiJavaCodeReferenceElement) &&
        !(e instanceof PsiJavaToken && !(e instanceof PsiKeyword)) &&
        canSelectXml(e) &&
        !isDocCommentElement(e);
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {

      final TextRange originalRange = e.getTextRange();
      List<TextRange> ranges = expandToWholeLine(editorText, originalRange, true);

      if (ranges.size() == 1 && ranges.contains(originalRange)) {
        ranges = expandToWholeLine(editorText, originalRange, false);
      }

      List<TextRange> result = new ArrayList<TextRange>();
      result.addAll(ranges);
      return result;
    }
  }

  static class WordSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return super.canSelect(e) ||
             e instanceof PsiJavaToken && ((PsiJavaToken)e).getTokenType() == JavaTokenType.IDENTIFIER;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> ranges;
      if (super.canSelect(e)) {
        ranges = super.select(e, editorText, cursorOffset, editor);
      }
      else {
        ranges = new ArrayList<TextRange>();
      }
      addWordSelection(editor.getSettings().isCamelWords(), editorText, cursorOffset, ranges);
      return ranges;
    }
  }

  public static void addWordSelection(boolean camel, CharSequence editorText, int cursorOffset, List<TextRange> ranges) {
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
             ((PsiLiteralExpression)element).getType().equalsToText("java.lang.String");
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

      TextRange range = e.getTextRange();
      final StringLiteralLexer lexer = new StringLiteralLexer('\"', JavaTokenType.STRING_LITERAL);
      char[] chars = CharArrayUtil.fromSequence(editorText);
      lexer.start(chars, range.getStartOffset(), range.getEndOffset());
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

  private static class CodeBlockOrInitializerSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiCodeBlock || e instanceof PsiArrayInitializerExpression;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = new ArrayList<TextRange>();

      PsiElement[] children = e.getChildren();

      int start = findOpeningBrace(children);
      int end = findClosingBrace(children);

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
        int end = findClosingBrace(children);

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

      while (endElement.getNextSibling() != null && endElement instanceof PsiJavaCodeReferenceElement) {
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

  static class DtdSelectioner implements Selectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof XmlAttlistDecl || e instanceof XmlElementDecl;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      PsiElement[] children = e.getChildren();

      PsiElement first = null;
      PsiElement last = null;
      for (PsiElement child : children) {
        if (child instanceof XmlToken) {
          XmlToken token = (XmlToken)child;
          if (token.getTokenType() == XmlTokenType.XML_TAG_END) {
            last = token;
            break;
          }
          if (token.getTokenType() == XmlTokenType.XML_ELEMENT_DECL_START ||
              token.getTokenType() == XmlTokenType.XML_ATTLIST_DECL_START
             ) {
            first = token;
          }
        }
      }

      List<TextRange> result = new ArrayList<TextRange>(1);
      if (first != null && last != null) {
        result.addAll(expandToWholeLine(editorText,
                                        new TextRange(first.getTextRange().getStartOffset(),
                                                      last.getTextRange().getEndOffset() + 1),
                                        false));
      }

      return result;
    }
  }

  static class XmlCDATAContentSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof CompositePsiElement &&
             ((CompositePsiElement)e).getElementType() == XmlElementType.XML_CDATA;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
      PsiElement[] children = e.getChildren();

      PsiElement first = null;
      PsiElement last = null;
      for (PsiElement child : children) {
        if (child instanceof XmlToken) {
          XmlToken token = (XmlToken)child;
          if (token.getTokenType() == XmlTokenType.XML_CDATA_START) {
            first = token.getNextSibling();
          }
          if (token.getTokenType() == XmlTokenType.XML_CDATA_END) {
            last = token.getPrevSibling();
            break;
          }
        }
      }

      if (first != null && last != null) {
        result.addAll(expandToWholeLine(editorText,
                                        new TextRange(first.getTextRange().getStartOffset(),
                                                      last.getTextRange().getEndOffset()),
                                        false));
      }

      return result;
    }
  }

  static class XmlTagSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof XmlTag;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      List<TextRange> result = super.select(e, editorText, cursorOffset, editor);
      PsiElement[] children = e.getChildren();

      addTagContentSelection(children, result, editorText);

      PsiElement prev = e.getPrevSibling();
      while (prev instanceof PsiWhiteSpace || prev instanceof XmlText || prev instanceof XmlComment) {
        if (prev instanceof XmlText && prev.getText().trim().length() > 0) break;
        if (prev instanceof XmlComment) {
          result.addAll(expandToWholeLine(editorText,
                                          new TextRange(prev.getTextRange().getStartOffset(),
                                                        e.getTextRange().getEndOffset()),
                                          false));
        }
        prev = prev.getPrevSibling();
      }

      return result;
    }

    private static void addTagContentSelection(final PsiElement[] children, final List<TextRange> result, final CharSequence editorText) {
      PsiElement first = null;
      PsiElement last = null;
      for (PsiElement child : children) {
        if (child instanceof XmlToken) {
          XmlToken token = (XmlToken)child;
          if (token.getTokenType() == XmlTokenType.XML_TAG_END) {
            first = token.getNextSibling();
          }
          if (token.getTokenType() == XmlTokenType.XML_END_TAG_START) {
            last = token.getPrevSibling();
            break;
          }
        }
      }

      if (first != null && last != null) {
        result.addAll(expandToWholeLine(editorText,
                                        new TextRange(first.getTextRange().getStartOffset(),
                                                      last.getTextRange().getEndOffset()),
                                        false));
      }
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
  
  static class XmlElementSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof XmlAttribute || e instanceof XmlAttributeValue;
    }
  }

  static class XmlTokenSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      final FileType fileType = e.getContainingFile().getFileType();
      return e instanceof XmlToken && 
             (fileType == StdFileTypes.XML ||
              fileType == StdFileTypes.DTD
             );
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
      XmlToken token = (XmlToken)e;

      if (token.getTokenType() != XmlTokenType.XML_DATA_CHARACTERS &&
          token.getTokenType() != XmlTokenType.XML_START_TAG_START &&
          token.getTokenType() != XmlTokenType.XML_END_TAG_START
        ) {
        List<TextRange> ranges = super.select(e, editorText, cursorOffset, editor);
        addWordSelection(editor.getSettings().isCamelWords(), editorText, cursorOffset, ranges);
        return ranges;
      }
      else {
        List<TextRange> result = new ArrayList<TextRange>();
        addWordSelection(editor.getSettings().isCamelWords(), editorText, cursorOffset, result);
        return result;
      }
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

  static class PlainTextLineSelectioner extends BasicSelectioner {
    public boolean canSelect(PsiElement e) {
      return e instanceof PsiPlainText || e instanceof XmlToken && ((XmlToken)e).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS;
    }

    public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
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
