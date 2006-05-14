/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.ide.DataManager;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;

public class EnterHandler extends EditorWriteActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.EnterHandler");

  private EditorActionHandler myOriginalHandler;
  private static final String DOC_COMMENT_SUFFIX = "*/";
  private static final String DOC_COMMENT_PREFIX = "/**";
  private static final String CSTYLE_COMMENT_PREFIX = "/*";
  private static final char DOC_COMMENT_ASTERISK = '*';
  private static final String DOC_COMMENT_ASTERISK_STRING = "*";

  public EnterHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    return myOriginalHandler.isEnabled(editor, dataContext);
  }

  public void executeWriteAction(final Editor editor, final DataContext dataContext) {
    final Project project = (Project)DataManager.getInstance().getDataContext(editor.getComponent()).getData(DataConstants.PROJECT);
    PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside(new Computable<Object>() {
      public Object compute() {
        executeWriteActionInner(editor, dataContext, project);
        return null;
      }
    });
  }

  public void executeWriteActionInner(Editor editor, DataContext dataContext, Project project) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (project == null) {
      myOriginalHandler.execute(editor, dataContext);
      return;
    }
    final Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (file == null) {
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    CommandProcessor.getInstance().setCurrentCommandName(CodeInsightBundle.message("command.name.typing"));

    EditorModificationUtil.deleteSelectedText(editor);

    int caretOffset = editor.getCaretModel().getOffset();
    CharSequence text = document.getCharsSequence();
    int length = document.getTextLength();
    if (caretOffset < length && text.charAt(caretOffset) != '\n') {
      int offset1 = CharArrayUtil.shiftBackward(text, caretOffset, " \t");
      if (offset1 < 0 || text.charAt(offset1) == '\n') {
        int offset2 = CharArrayUtil.shiftForward(text, offset1 + 1, " \t");
        boolean isEmptyLine = offset2 >= length || text.charAt(offset2) == '\n';
        if (!isEmptyLine) { // we are in leading spaces of a non-empty line
          myOriginalHandler.execute(editor, dataContext);
          return;
        }
      }
    }

    PsiDocumentManager.getInstance(project).commitDocument(document);
    PsiElement psiAtOffset = file.findElementAt(caretOffset);
    if (file instanceof PropertiesFile) {
      handleEnterInPropertiesFile(editor, document, psiAtOffset, caretOffset);
      return;
    }
    boolean forceIndent = false;
    int caretAdvance = 0;
    if (psiAtOffset instanceof PsiJavaToken && psiAtOffset.getTextOffset() < caretOffset) {
      PsiJavaToken token = (PsiJavaToken)psiAtOffset;
      if (token.getTokenType() == JavaTokenType.STRING_LITERAL) {
        TextRange range = token.getTextRange();
        final StringLiteralLexer lexer = new StringLiteralLexer('\"', JavaTokenType.STRING_LITERAL);
        char[] chars = CharArrayUtil.fromSequence(text);
        lexer.start(chars, range.getStartOffset(), range.getEndOffset());
        while (lexer.getTokenType() != null) {
          if (lexer.getTokenStart() < caretOffset && caretOffset < lexer.getTokenEnd()) {
            if (StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(lexer.getTokenType())) {
              caretOffset = lexer.getTokenEnd();
            }
            break;
          }
          lexer.advance();
        }

        document.insertString(caretOffset, "\" + \"");
        text = document.getCharsSequence();
        caretOffset += "\" +".length();
        caretAdvance = 1;
        if (CodeStyleSettingsManager.getSettings(project).BINARY_OPERATION_SIGN_ON_NEXT_LINE) {
          caretOffset -= 1;
          caretAdvance = 3;
        }
        forceIndent = true;
      }
      else if (token.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
        int offset = CharArrayUtil.shiftForward(text, caretOffset, " \t");
        if (offset < document.getTextLength() && text.charAt(offset) != '\n') {
          document.insertString(caretOffset, "// ");
          text = document.getCharsSequence();
        }
      }
    }

    if (settings.INSERT_BRACE_ON_ENTER && isAfterUnmatchedLBrace(editor, caretOffset, file.getFileType())) {
      int offset = CharArrayUtil.shiftForward(text, caretOffset, " \t");
      if (offset < document.getTextLength()) {
        char c = text.charAt(offset);
        if (c != ')' && c != ']' && c != ';' && c != ',' && c != '%') {
          offset = CharArrayUtil.shiftForwardUntil(text, caretOffset, "\n");
        }
      }
      offset = Math.min(offset, document.getTextLength());

      document.insertString(offset, "\n}");
      PsiDocumentManager.getInstance(project).commitDocument(document);
      try {
        CodeStyleManager.getInstance(project).adjustLineIndent(file, offset + 1);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      text = document.getCharsSequence();
      forceIndent = true;
    }

    if (settings.INSERT_SCRIPTLET_END_ON_ENTER && isAfterUnmatchedScriplet(editor, caretOffset)) {
      document.insertString(caretOffset, "%>");
      myOriginalHandler.execute(editor, dataContext);
      text = document.getCharsSequence();
      forceIndent = true;
    }

    if (settings.SMART_INDENT_ON_ENTER) {
      // special case: enter inside "()" or "{}"
      if (caretOffset > 0 && caretOffset < text.length() && ((text.charAt(caretOffset - 1) == '(' && text.charAt(caretOffset) == ')') ||
                                                             (text.charAt(caretOffset - 1) == '{' && text.charAt(caretOffset) == '}'))) {
        myOriginalHandler.execute(editor, dataContext);
        PsiDocumentManager.getInstance(project).commitDocument(document);
        try {
          CodeStyleManager.getInstance(project).adjustLineIndent(file, editor.getCaretModel().getOffset());
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        text = document.getCharsSequence();
      }
    }

    if (file instanceof XmlFile && isBetweenXmlTags(editor, caretOffset)) {
      myOriginalHandler.execute(editor, dataContext);
      text = document.getCharsSequence();
      forceIndent = true;
    }

    boolean isFirstColumn = caretOffset == 0 || text.charAt(caretOffset - 1) == '\n';
    final boolean insertSpace =
      !isFirstColumn && !(caretOffset >= document.getTextLength() || text.charAt(caretOffset) == ' ' || text.charAt(caretOffset) == '\t');
    editor.getCaretModel().moveToOffset(caretOffset);
    myOriginalHandler.execute(editor, dataContext);

    if (settings.SMART_INDENT_ON_ENTER || forceIndent) {
      caretOffset += 1;
      caretOffset = CharArrayUtil.shiftForward(editor.getDocument().getCharsSequence(), caretOffset, " \t");
    }
    else {
      caretOffset = editor.getCaretModel().getOffset();
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final DoEnterAction action = new DoEnterAction(file, editor, document, caretOffset, !insertSpace, caretAdvance);
    action.setForceIndent(forceIndent);
    action.run();
  }

  private static void handleEnterInPropertiesFile(final Editor editor,
                                                  final Document document,
                                                  final PsiElement psiAtOffset,
                                                  int caretOffset) {
    String text = document.getText();
    String line = text.substring(0, caretOffset);
    int i = line.lastIndexOf('\n');
    if (i > 0) {
      line = line.substring(i);
    }
    final String toInsert;
    if (PropertiesUtil.isUnescapedBackSlashAtTheEnd(line)) {
      toInsert = "\n  ";
    }
    else {
      final IElementType elementType = psiAtOffset == null ? null : psiAtOffset.getNode().getElementType();

      if (elementType == PropertiesTokenTypes.VALUE_CHARACTERS) {
        toInsert = "\\\n  ";
      }
      else if (elementType == PropertiesTokenTypes.END_OF_LINE_COMMENT) {
        toInsert = "\n#";
      }
      else {
        toInsert = "\n";
      }
    }
    document.insertString(caretOffset, toInsert);
    caretOffset+=toInsert.length();
    editor.getCaretModel().moveToOffset(caretOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }

  private static boolean isAfterUnmatchedScriplet(Editor editor, int offset) {
    CharSequence chars = editor.getDocument().getCharsSequence();

    if (!(offset >= 3 && chars.charAt(offset - 1) == '!' && chars.charAt(offset - 2) == '%' && chars.charAt(offset - 3) == '<') &&
        !(offset >= 2 && chars.charAt(offset - 1) == '%' && chars.charAt(offset - 2) == '<')) {
      return false;
    }

    EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 2);
    if (iterator.getTokenType() != JspTokenType.JSP_SCRIPTLET_START
        && iterator.getTokenType() != JspTokenType.JSP_DECLARATION_START) {
      return false;
    }

    iterator = highlighter.createIterator(offset);
    while (!iterator.atEnd()) {
      IElementType tokenType = iterator.getTokenType();

      if (tokenType == JspTokenType.JSP_SCRIPTLET_START || tokenType == JspTokenType.JSP_DECLARATION_START) {
        return true;
      }
      if (tokenType == JspTokenType.JSP_SCRIPTLET_END || tokenType == JspTokenType.JSP_DECLARATION_END) {
        return false;
      }
      iterator.advance();
    }

    return true;
  }

  private static boolean isCommentComplete(PsiComment comment) {
    String commentText = comment.getText();
    if (!commentText.endsWith(DOC_COMMENT_SUFFIX)) return false;

    Lexer lexer = new JavaLexer(PsiUtil.getLanguageLevel(comment));
    lexer.start(commentText.toCharArray(), DOC_COMMENT_PREFIX.length(), commentText.length());
    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) {
        return false;
      }
      if (tokenType == JavaTokenType.STRING_LITERAL || tokenType == JavaTokenType.CHARACTER_LITERAL) {
        String text = commentText.substring(lexer.getTokenStart(), lexer.getTokenEnd());
        int endOffset = comment.getTextRange().getEndOffset();
        if (text.endsWith(DOC_COMMENT_SUFFIX) &&
            endOffset < comment.getContainingFile().getTextLength()-1 &&
            comment.getContainingFile().getText().charAt(endOffset+1) == '\n') {
          return true;
        }
      }
      if (lexer.getTokenEnd() == commentText.length()) {
        return lexer.getTokenEnd() - lexer.getTokenStart() == 1;
      }
      if (tokenType == JavaDocElementType.DOC_COMMENT || tokenType == JavaTokenType.C_STYLE_COMMENT) {
        return false;
      }
      lexer.advance();
    }
  }

  public static boolean isAfterUnmatchedLBrace(Editor editor, int offset, FileType fileType) {
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    if (chars.charAt(offset - 1) != '{') return false;

    EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 1);
    BraceMatchingUtil.BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);

    if (!braceMatcher.isLBraceToken(iterator, chars, fileType) ||
        !braceMatcher.isStructuralBrace(iterator, chars, fileType)
        ) {
      return false;
    }

    Language language = iterator.getTokenType().getLanguage();

    iterator = highlighter.createIterator(0);
    int balance = 0;
    while (!iterator.atEnd()) {
      IElementType tokenType = iterator.getTokenType();
      if (tokenType.getLanguage().equals(language)) {
        if (braceMatcher.isStructuralBrace(iterator, chars, fileType)) {
          if (braceMatcher.isLBraceToken(iterator, chars, fileType)) {
            balance++;
          } else if (braceMatcher.isRBraceToken(iterator, chars, fileType)) {
            balance--;
          }
        }
      }
      iterator.advance();
    }
    return balance > 0;
  }

  private static boolean isBetweenXmlTags(Editor editor, int offset) {
    return isBetweenTags(editor,offset,XmlTokenType.XML_TAG_END,XmlTokenType.XML_END_TAG_START);
  }

  private static boolean isBetweenTags(Editor editor, int offset, IElementType first, IElementType second) {
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    if (chars.charAt(offset - 1) != '>') return false;

    EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 1);
    if (iterator.getTokenType() != first) return false;
    iterator.advance();
    return !iterator.atEnd() && iterator.getTokenType() == second;
  }

  private static class DoEnterAction implements Runnable {
    private PsiFile myFile;
    private int myOffset;
    private Document myDocument;
    private boolean myInsertSpace;
    private final Editor myEditor;
    private int myCaretAdvance;

    private boolean myForceIndent = false;
    private static final String LINE_SEPARATOR = "\n";

    @NonNls private static final String PARAM_TAG = "@param";
    @NonNls private static final String RETURN_TAG = "@return";
    @NonNls private static final String THROWS_TAG = "@throws";


    public DoEnterAction(
      PsiFile file,
      Editor view,
      Document document,
      int offset,
      boolean insertSpace,
      int caretAdvance) {
      myEditor = view;
      myFile = file;
      myOffset = offset;
      myDocument = document;
      myInsertSpace = insertSpace;
      myCaretAdvance = caretAdvance;
    }

    public void setForceIndent(boolean forceIndent) {
      myForceIndent = forceIndent;
    }

    public void run() {
      try {
        final CharSequence chars = myDocument.getCharsSequence();

        int offset = CharArrayUtil.shiftBackwardUntil(chars, myOffset - 1, LINE_SEPARATOR) - 1;
        offset = CharArrayUtil.shiftBackwardUntil(chars, offset, LINE_SEPARATOR) + 1;
        int lineStart = CharArrayUtil.shiftForward(chars, offset, " \t");

        boolean isInsideJava = PsiUtil.getLanguageAtOffset(myFile, offset) == StdLanguages.JAVA;

        boolean docStart = isInsideJava && CharArrayUtil.regionMatches(chars, lineStart, DOC_COMMENT_PREFIX);
        boolean cStyleStart = isInsideJava && CharArrayUtil.regionMatches(chars, lineStart, CSTYLE_COMMENT_PREFIX);
        boolean docAsterisk = isInsideJava && CharArrayUtil.regionMatches(chars, lineStart, DOC_COMMENT_ASTERISK_STRING);
        boolean slashSlash = isInsideJava && CharArrayUtil.regionMatches(chars, lineStart, "//") &&
                             chars.charAt(CharArrayUtil.shiftForward(chars, myOffset, " \t")) != '\n';

        if (docStart) {
          PsiElement element = myFile.findElementAt(lineStart);
          if (element.getText().equals(DOC_COMMENT_PREFIX) && element.getParent() instanceof PsiDocComment) {
            PsiDocComment comment = (PsiDocComment)element.getParent();
            int commentEnd = comment.getTextRange().getEndOffset();
            if (myOffset >= commentEnd) {
              docStart = false;
            }
            else {
              if (isCommentComplete(comment)) {
                if (myOffset >= commentEnd) {
                  docAsterisk = false;
                  docStart = false;
                }
                else {
                  docAsterisk = true;
                  docStart = false;
                }
              }
              else {
                generateJavadoc();
              }
            }
          }
          else {
            docStart = false;
          }
        }
        else if (cStyleStart) {
          PsiElement element = myFile.findElementAt(lineStart);
          if (element instanceof PsiComment && ((PsiComment)element).getTokenType() == JavaTokenType.C_STYLE_COMMENT) {
            final PsiComment comment = (PsiComment)element;
            int commentEnd = comment.getTextRange().getEndOffset();
            if (myOffset >= commentEnd) {
              docStart = false;
            }
            else {
              if (isCommentComplete(comment)) {
                if (myOffset >= commentEnd) {
                  docAsterisk = false;
                  docStart = false;
                }
                else {
                  docAsterisk = true;
                  docStart = false;
                }
              }
              else {
                myDocument.insertString(myOffset, " " + DOC_COMMENT_SUFFIX);
                int lstart = CharArrayUtil.shiftBackwardUntil(chars, myOffset, "\n");
                myDocument.insertString(myOffset, chars.subSequence(lstart, myOffset));
              }
            }
          }
          else {
            docStart = false;
          }
        }

        if (docAsterisk) {
          docAsterisk = insertDocAsterisk(lineStart, docAsterisk);
        }

        if (CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER || myForceIndent || docStart || docAsterisk ||
            slashSlash) {
          myOffset = CodeStyleManager.getInstance(getProject()).adjustLineIndent(myFile, myOffset);
        }

        if (docAsterisk || docStart || slashSlash) {
          if (myInsertSpace) {
            if (myOffset == myDocument.getTextLength()) {
              myDocument.insertString(myOffset, " ");
            }
            myDocument.insertString(myOffset + 1, " ");
          }

          final char c = myDocument.getCharsSequence().charAt(myOffset);
          if (c != '\n') {
            myOffset += 1;
          }
        }

        if ((docAsterisk || slashSlash) && !docStart) {
          myCaretAdvance = slashSlash ? 2 : 1;
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

      myEditor.getCaretModel().moveToOffset(myOffset);
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      myEditor.getSelectionModel().removeSelection();
      if (myCaretAdvance != 0) {
        LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();
        LogicalPosition pos = new LogicalPosition(caretPosition.line, caretPosition.column + myCaretAdvance);
        myEditor.getCaretModel().moveToLogicalPosition(pos);
      }
    }

    private void generateJavadoc() throws IncorrectOperationException {
      CodeInsightSettings settings = CodeInsightSettings.getInstance();
      StringBuffer buffer = new StringBuffer();
      buffer.append(DOC_COMMENT_ASTERISK);
      buffer.append(LINE_SEPARATOR);
      buffer.append(DOC_COMMENT_SUFFIX);

      PsiDocComment comment = createComment(buffer, settings);

      myOffset = comment.getTextRange().getStartOffset();
      myOffset = CharArrayUtil.shiftForwardUntil(myDocument.getCharsSequence(), myOffset, LINE_SEPARATOR);
      myOffset = CharArrayUtil.shiftForward(myDocument.getCharsSequence(), myOffset, LINE_SEPARATOR);
      myOffset = CharArrayUtil.shiftForwardUntil(myDocument.getCharsSequence(), myOffset, LINE_SEPARATOR);
      removeTrailingSpaces(myDocument, myOffset);

      if (!CodeStyleSettingsManager.getSettings(getProject()).JD_LEADING_ASTERISKS_ARE_ENABLED) {
        LOG.assertTrue(myDocument.getCharsSequence().charAt(myOffset - 1) == DOC_COMMENT_ASTERISK);
        myDocument.deleteString(myOffset - 1, myOffset);
        myOffset--;
      } else {
        myDocument.insertString(myOffset, " ");
        myOffset++;
      }

      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }

    private PsiDocComment createComment(final StringBuffer buffer, final CodeInsightSettings settings)
      throws IncorrectOperationException {
      myDocument.insertString(myOffset, buffer.toString());

      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      CodeStyleManager.getInstance(getProject()).adjustLineIndent(myFile, myOffset + buffer.length() - 2);

      PsiDocComment comment = PsiTreeUtil.getParentOfType(myFile.findElementAt(myOffset),
                                                          PsiDocComment.class);

      comment = createJavaDocStub(settings, comment, getProject());

      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(getProject());
      comment = (PsiDocComment)codeStyleManager.reformat(comment);
      PsiElement next = comment.getNextSibling();
      if (!(next instanceof PsiWhiteSpace) || -1 == next.getText().indexOf(LINE_SEPARATOR)) {
        int lineBreakOffset = comment.getTextRange().getEndOffset();
        myDocument.insertString(lineBreakOffset, LINE_SEPARATOR);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        codeStyleManager.adjustLineIndent(myFile, lineBreakOffset + 1);
        comment = PsiTreeUtil.getParentOfType(myFile.findElementAt(myOffset), PsiDocComment.class);
      }
      return comment;
    }

    private PsiDocComment createJavaDocStub(final CodeInsightSettings settings,
                                            final PsiDocComment comment,
                                            final Project project) {
      final PsiElement context = comment.getParent();
      if (settings.JAVADOC_STUB_ON_ENTER) {
        if (context instanceof PsiMethod) {
          PsiMethod psiMethod = (PsiMethod)context;
          if (psiMethod.getDocComment() != comment) return comment;

          final StringBuffer buffer = new StringBuffer();

          final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          for (PsiParameter parameter : parameters) {
            buffer.append(createDocCommentLine(PARAM_TAG, getProject()));
            buffer.append(parameter.getName());
            buffer.append(LINE_SEPARATOR);
          }

          if (psiMethod.getReturnType() != null && psiMethod.getReturnType() != PsiType.VOID) {
            buffer.append(createDocCommentLine(RETURN_TAG, getProject()));
            buffer.append(LINE_SEPARATOR);
          }

          final PsiJavaCodeReferenceElement[] references = psiMethod.getThrowsList().getReferenceElements();
          for (PsiJavaCodeReferenceElement reference : references) {
            buffer.append(createDocCommentLine(THROWS_TAG, getProject()));
            buffer.append(reference.getText());
            buffer.append(LINE_SEPARATOR);
          }

          if (buffer.length() != 0) {
            myOffset = CharArrayUtil.shiftForwardUntil(myDocument.getCharsSequence(), myOffset, LINE_SEPARATOR);
            myOffset = CharArrayUtil.shiftForward(myDocument.getCharsSequence(), myOffset, LINE_SEPARATOR);
            myDocument.insertString(myOffset, buffer.toString());
          }
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();
        return PsiTreeUtil.getParentOfType(myFile.findElementAt(myOffset), PsiDocComment.class);
      }
      return comment;
    }

    private Project getProject() {
      return myFile.getProject();
    }

    private static void removeTrailingSpaces(final Document document, final int offset) {
      int startOffset = offset;
      int endOffset = offset;

      final CharSequence charsSequence = document.getCharsSequence();

      for (int i = startOffset; i < charsSequence.length(); i++) {
        final char c = charsSequence.charAt(i);
        endOffset = i;
        if (c == '\n') {
          break;
        }
        if (c != ' ' && c != '\t') {
          return;
        }
      }

      document.deleteString(startOffset, endOffset);
    }

    private boolean insertDocAsterisk(int lineStart, boolean docAsterisk) {
      PsiElement element = myFile.findElementAt(lineStart);

      if (element.getText().equals(DOC_COMMENT_ASTERISK_STRING) || element.getText().equals(DOC_COMMENT_PREFIX)) {
        PsiDocComment comment = null;
        if (element.getParent() instanceof PsiDocComment) {
          comment = (PsiDocComment)element.getParent();
        }
        else if (element.getParent() instanceof PsiDocTag && element.getParent().getParent() instanceof PsiDocComment) {
          comment = (PsiDocComment)element.getParent().getParent();
        }
        if (comment != null) {
          int commentEnd = comment.getTextRange().getEndOffset();
          if (myOffset >= commentEnd) {
            docAsterisk = false;
          }
          else {
            removeTrailingSpaces(myDocument, myOffset);
            myDocument.insertString(myOffset, createDocCommentLine("", getProject()));
            PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
          }
        }
        else {
          docAsterisk = false;
        }
      }
      else if (element instanceof PsiComment && ((PsiComment)element).getTokenType() == JavaTokenType.C_STYLE_COMMENT) {
        // Check if C-Style comment already uses asterisks.
        boolean usesAstersk = false;
        int commentLine = myDocument.getLineNumber(element.getTextRange().getStartOffset());
        if (commentLine < myDocument.getLineCount() - 1) {
          int nextLineOffset = myDocument.getLineStartOffset(commentLine + 1);
          if (nextLineOffset < element.getTextRange().getEndOffset()) {
            final CharSequence chars = myDocument.getCharsSequence();
            nextLineOffset = CharArrayUtil.shiftForward(chars, nextLineOffset, " \t");
            usesAstersk = chars.charAt(nextLineOffset) == '*';
          }
        }
        if (usesAstersk) {
          removeTrailingSpaces(myDocument, myOffset);
          myDocument.insertString(myOffset, "* ");
          PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        }
        docAsterisk = usesAstersk;
      }
      else {
        docAsterisk = false;
      }
      return docAsterisk;
    }
  }

  private static String createDocCommentLine(String lineData, Project project) {
    if (!CodeStyleSettingsManager.getSettings(project).JD_LEADING_ASTERISKS_ARE_ENABLED) {
      return " " + lineData + " ";
    } else {
      if (lineData.length() == 0) {
        return DOC_COMMENT_ASTERISK + " ";
      } else {
        return DOC_COMMENT_ASTERISK + " " + lineData + " ";
      }

    }
  }
}
