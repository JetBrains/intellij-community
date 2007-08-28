/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.ide.DataManager;
import com.intellij.lang.*;
import com.intellij.lang.documentation.CodeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
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
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nullable;

public class EnterHandler extends EditorWriteActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.EnterHandler");

  private final EditorActionHandler myOriginalHandler;

  public EnterHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    return myOriginalHandler.isEnabled(editor, dataContext);
  }

  public void executeWriteAction(final Editor editor, final DataContext dataContext) {
    final Project project = DataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
    if (project != null) {
      PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside(new Runnable() {
        public void run() {
          executeWriteActionInner(editor, dataContext, project);
        }
      });
    }
    else {
      executeWriteActionInner(editor, dataContext, project);
    }
  }

  private void executeWriteActionInner(Editor editor, DataContext dataContext, Project project) {
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
    if (psiAtOffset != null && psiAtOffset.getTextOffset() < caretOffset) {
      ASTNode token = psiAtOffset.getNode();
      final Language language = psiAtOffset.getLanguage();
      final Commenter languageCommenter = language.getCommenter();
      final CodeDocumentationAwareCommenter commenter = languageCommenter instanceof CodeDocumentationAwareCommenter
                                                        ? (CodeDocumentationAwareCommenter)languageCommenter:null;
      final TypedHandler.QuoteHandler fileTypeQuoteHandler = TypedHandler.getQuoteHandler(psiAtOffset.getContainingFile().getFileType());
      TypedHandler.JavaLikeQuoteHandler quoteHandler = fileTypeQuoteHandler instanceof TypedHandler.JavaLikeQuoteHandler ?
                                                       (TypedHandler.JavaLikeQuoteHandler) fileTypeQuoteHandler:null;

      if (quoteHandler != null &&
          quoteHandler.getConcatenatableStringTokenTypes() != null &&
          quoteHandler.getConcatenatableStringTokenTypes().contains(token.getElementType())) {
        TextRange range = token.getTextRange();
        final char literalStart = token.getText().charAt(0);
        final StringLiteralLexer lexer = new StringLiteralLexer(literalStart, token.getElementType());
        lexer.start(text, range.getStartOffset(), range.getEndOffset(),0);

        while (lexer.getTokenType() != null) {
          if (lexer.getTokenStart() < caretOffset && caretOffset < lexer.getTokenEnd()) {
            if (StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(lexer.getTokenType())) {
              caretOffset = lexer.getTokenEnd();
            }
            break;
          }
          lexer.advance();
        }

        if (psiAtOffset.getParent() instanceof PsiLiteralExpression && psiAtOffset.getParent().getParent() instanceof PsiReferenceExpression) {
          document.insertString(psiAtOffset.getTextRange().getEndOffset(), ")");
          document.insertString(psiAtOffset.getTextRange().getStartOffset(), "(");
          caretOffset++;
          caretAdvance++;
        }        

        final String insertedFragment = literalStart + " " + quoteHandler.getStringConcatenationOperatorRepresentation();
        document.insertString(caretOffset, insertedFragment + " " + literalStart);
        text = document.getCharsSequence();
        caretOffset += insertedFragment.length();
        caretAdvance = 1;
        if (CodeStyleSettingsManager.getSettings(project).BINARY_OPERATION_SIGN_ON_NEXT_LINE) {
          caretOffset -= 1;
          caretAdvance = 3;
        }
        forceIndent = true;
      }
      else if (commenter != null && token.getElementType() == commenter.getLineCommentTokenType() ) {
        final int offset = CharArrayUtil.shiftForward(text, caretOffset, " \t");

        if (offset < document.getTextLength() && text.charAt(offset) != '\n') {
          assert commenter.getLineCommentPrefix() != null:"Line Comment type is set but Line Comment Prefix is null!";
          document.insertString(caretOffset, commenter.getLineCommentPrefix() + " ");
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

  private static boolean isCommentComplete(PsiComment comment, CodeDocumentationAwareCommenter commenter) {
    String commentText = comment.getText();
    final String expectedCommentEnd = comment instanceof PsiDocComment ? commenter.getDocumentationCommentSuffix():commenter.getBlockCommentSuffix();
    if (!commentText.endsWith(expectedCommentEnd)) return false;

    final PsiFile containingFile = comment.getContainingFile();
    final Language language = comment.getParent().getLanguage();
    Lexer lexer = language == StdLanguages.JAVA ? new JavaLexer(PsiUtil.getLanguageLevel(comment)):language.getParserDefinition().createLexer(containingFile.getProject());
    lexer.start(commentText, commenter.getDocumentationCommentPrefix().length(), commentText.length(),0);
    TypedHandler.QuoteHandler fileTypeHandler = TypedHandler.getQuoteHandler(containingFile.getFileType());
    TypedHandler.JavaLikeQuoteHandler javaLikeQuoteHandler = fileTypeHandler instanceof TypedHandler.JavaLikeQuoteHandler ?
                                                             (TypedHandler.JavaLikeQuoteHandler)fileTypeHandler:null;

    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) {
        return false;
      }

      if (javaLikeQuoteHandler != null &&
          javaLikeQuoteHandler.getStringTokenTypes() != null &&
          javaLikeQuoteHandler.getStringTokenTypes().contains(tokenType)) {
        String text = commentText.substring(lexer.getTokenStart(), lexer.getTokenEnd());
        int endOffset = comment.getTextRange().getEndOffset();

        if (text.endsWith(expectedCommentEnd) &&
            endOffset < containingFile.getTextLength() &&
            containingFile.getText().charAt(endOffset) == '\n') {
          return true;
        }
      }
      if (lexer.getTokenEnd() == commentText.length()) {
        return lexer.getTokenEnd() - lexer.getTokenStart() == 1;
      }
      if (tokenType == commenter.getDocumentationCommentTokenType() || tokenType == commenter.getBlockCommentTokenType()) {
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
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    if (chars.charAt(offset - 1) != '>') return false;

    EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 1);
    if (iterator.getTokenType() != XmlTokenType.XML_TAG_END) return false;
    iterator.retreat();

    int retrieveCount = 1;
    while(!iterator.atEnd()) {
      final IElementType tokenType = iterator.getTokenType();
      if (tokenType == XmlTokenType.XML_END_TAG_START) return false;
      if (tokenType == XmlTokenType.XML_START_TAG_START) break;
      ++retrieveCount;
      iterator.retreat();
    }

    for(int i = 0; i < retrieveCount; ++i) iterator.advance();
    iterator.advance();
    return !iterator.atEnd() && iterator.getTokenType() == XmlTokenType.XML_END_TAG_START;
  }

  private static class DoEnterAction implements Runnable {
    private final PsiFile myFile;
    private int myOffset;
    private final Document myDocument;
    private final boolean myInsertSpace;
    private final Editor myEditor;
    private int myCaretAdvance;

    private boolean myForceIndent = false;
    private static final String LINE_SEPARATOR = "\n";

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

        final Commenter langCommenter = PsiUtil.getLanguageAtOffset(myFile, offset).getCommenter();
        final boolean isInsideJavalikeCode = langCommenter instanceof CodeDocumentationAwareCommenter;
        final CodeDocumentationAwareCommenter commenter = isInsideJavalikeCode ? (CodeDocumentationAwareCommenter)langCommenter:null;

        boolean docStart = isInsideJavalikeCode &&
                           commenter.getDocumentationCommentPrefix() != null &&
                           CharArrayUtil.regionMatches(chars, lineStart, commenter.getDocumentationCommentPrefix());
        boolean cStyleStart = isInsideJavalikeCode &&
                              commenter.getBlockCommentPrefix() != null &&
                              CharArrayUtil.regionMatches(chars, lineStart, commenter.getBlockCommentPrefix());
        boolean docAsterisk = isInsideJavalikeCode &&
                              commenter.getDocumentationCommentLinePrefix() != null &&
                              CharArrayUtil.regionMatches(chars, lineStart, commenter.getDocumentationCommentLinePrefix());
        final int firstNonSpaceInLine = CharArrayUtil.shiftForward(chars, myOffset, " \t");
        boolean slashSlash = isInsideJavalikeCode &&
                             commenter.getLineCommentPrefix() != null &&
                             CharArrayUtil.regionMatches(chars, lineStart, commenter.getLineCommentPrefix()) &&
                             firstNonSpaceInLine < chars.length() && chars.charAt(firstNonSpaceInLine) != '\n';

        if (docStart) {
          PsiElement element = myFile.findElementAt(lineStart);
          final String text = element.getText();
          final PsiElement parent = element.getParent();

          if ((text.equals(commenter.getDocumentationCommentPrefix()) && parent instanceof PsiDocComment) ||
              (text.startsWith(commenter.getDocumentationCommentPrefix()) && element instanceof PsiComment)
             ) {
            PsiComment comment = parent instanceof PsiDocComment? (PsiDocComment)parent:(PsiComment)element;
            int commentEnd = comment.getTextRange().getEndOffset();

            if (myOffset >= commentEnd) {
              docStart = false;
            }
            else {
              if (isCommentComplete(comment, commenter)) {
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
                generateJavadoc(commenter);
              }
            }
          }
          else {
            docStart = false;
          }
        }
        else if (cStyleStart) {
          PsiElement element = myFile.findElementAt(lineStart);
          if (element instanceof PsiComment && commenter.getBlockCommentTokenType() == ((PsiComment)element).getTokenType()) {
            final PsiComment comment = (PsiComment)element;
            int commentEnd = comment.getTextRange().getEndOffset();
            if (myOffset >= commentEnd) {
              docStart = false;
            }
            else {
              if (isCommentComplete(comment, commenter)) {
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
                final int currentEndOfLine = CharArrayUtil.shiftForwardUntil(chars, myOffset, "\n");
                myDocument.insertString(currentEndOfLine, " " + commenter.getBlockCommentSuffix());
                int lstart = CharArrayUtil.shiftBackwardUntil(chars, myOffset, "\n");
                myDocument.insertString(currentEndOfLine, chars.subSequence(lstart, myOffset));
              }
            }
          }
          else {
            docStart = false;
          }
        }

        if (docAsterisk) {
          docAsterisk = insertDocAsterisk(lineStart, docAsterisk, commenter);
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
          myCaretAdvance = slashSlash ? commenter.getLineCommentPrefix().length() : 1;
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

    private void generateJavadoc(CodeDocumentationAwareCommenter commenter) throws IncorrectOperationException {
      CodeInsightSettings settings = CodeInsightSettings.getInstance();
      StringBuffer buffer = new StringBuffer();
      final String docCommentLinePrefix = commenter.getDocumentationCommentLinePrefix();
      if(docCommentLinePrefix==null){
        return;
      }
      buffer.append(docCommentLinePrefix);
      buffer.append(LINE_SEPARATOR);
      buffer.append(commenter.getDocumentationCommentSuffix());

      PsiComment comment = createComment(buffer, settings);
      if(comment==null){
        return;
      }

      myOffset = comment.getTextRange().getStartOffset();
      myOffset = CharArrayUtil.shiftForwardUntil(myDocument.getCharsSequence(), myOffset, LINE_SEPARATOR);
      myOffset = CharArrayUtil.shiftForward(myDocument.getCharsSequence(), myOffset, LINE_SEPARATOR);
      myOffset = CharArrayUtil.shiftForwardUntil(myDocument.getCharsSequence(), myOffset, LINE_SEPARATOR);
      removeTrailingSpaces(myDocument, myOffset);

      if (!CodeStyleSettingsManager.getSettings(getProject()).JD_LEADING_ASTERISKS_ARE_ENABLED) {
        LOG.assertTrue(CharArrayUtil.regionMatches(myDocument.getCharsSequence(),myOffset - docCommentLinePrefix.length(), docCommentLinePrefix));
        myDocument.deleteString(myOffset - docCommentLinePrefix.length(), myOffset);
        myOffset--;
      } else {
        myDocument.insertString(myOffset, " ");
        myOffset++;
      }

      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }

    @Nullable
    private PsiComment createComment(final StringBuffer buffer, final CodeInsightSettings settings)
      throws IncorrectOperationException {
      myDocument.insertString(myOffset, buffer.toString());

      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      CodeStyleManager.getInstance(getProject()).adjustLineIndent(myFile, myOffset + buffer.length() - 2);

      PsiComment comment = PsiTreeUtil.getNonStrictParentOfType(myFile.findElementAt(myOffset),
                                                          PsiDocComment.class, PsiComment.class);

      comment = createJavaDocStub(settings, comment, getProject());

      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(getProject());
      comment = (PsiComment)codeStyleManager.reformat(comment);
      PsiElement next = comment.getNextSibling();
      if (!(next instanceof PsiWhiteSpace) || !next.getText().contains(LINE_SEPARATOR)) {
        int lineBreakOffset = comment.getTextRange().getEndOffset();
        myDocument.insertString(lineBreakOffset, LINE_SEPARATOR);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        codeStyleManager.adjustLineIndent(myFile, lineBreakOffset + 1);
        comment = PsiTreeUtil.getNonStrictParentOfType(myFile.findElementAt(myOffset), PsiDocComment.class, PsiComment.class);
      }
      return comment;
    }

    private PsiComment createJavaDocStub(final CodeInsightSettings settings,
                                            final PsiComment comment,
                                            final Project project) {
      if (settings.JAVADOC_STUB_ON_ENTER) {
        final DocumentationProvider langDocumentationProvider = comment.getParent().getLanguage().getDocumentationProvider();
        final CodeDocumentationProvider docProvider = langDocumentationProvider instanceof CodeDocumentationProvider ?
                                                          (CodeDocumentationProvider)langDocumentationProvider:null;
        if (docProvider != null) {

          if (docProvider.findExistingDocComment(comment) != comment) return comment;
          String docStub = docProvider.generateDocumentationContentStub(comment);
          
          if (docStub != null && docStub.length() != 0) {
            myOffset = CharArrayUtil.shiftForwardUntil(myDocument.getCharsSequence(), myOffset, LINE_SEPARATOR);
            myOffset = CharArrayUtil.shiftForward(myDocument.getCharsSequence(), myOffset, LINE_SEPARATOR);
            myDocument.insertString(myOffset, docStub);
          }
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();
        return PsiTreeUtil.getNonStrictParentOfType(myFile.findElementAt(myOffset), PsiDocComment.class, PsiComment.class);
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

    private boolean insertDocAsterisk(int lineStart, boolean docAsterisk, CodeDocumentationAwareCommenter commenter) {
      PsiElement atLineStart = myFile.findElementAt(lineStart);
      if (atLineStart == null) return false;

      final String documentationCommentLinePrefix = commenter.getDocumentationCommentLinePrefix();
      final String docommentPrefix = commenter.getDocumentationCommentPrefix();
      final String text = atLineStart.getText();
      final TextRange textRange = atLineStart.getTextRange();

      if ((text.equals(documentationCommentLinePrefix) ||
           text.equals(docommentPrefix)) ||
           text.regionMatches(lineStart - textRange.getStartOffset(), docommentPrefix, 0, docommentPrefix.length()) ||
           text.regionMatches(lineStart - textRange.getStartOffset(), documentationCommentLinePrefix, 0 , documentationCommentLinePrefix.length())
        ) {
        PsiElement element = myFile.findElementAt(myOffset);
        if (element == null) return false;

        PsiComment comment = (element instanceof PsiComment)
                             ? (PsiComment) element
                             : PsiTreeUtil.getParentOfType(element, PsiDocComment.class, false);
        if (comment != null) {
          int commentEnd = comment.getTextRange().getEndOffset();
          if (myOffset >= commentEnd) {
            docAsterisk = false;
          }
          else {
            removeTrailingSpaces(myDocument, myOffset);
            myDocument.insertString(myOffset, JavaDocumentationProvider.createDocCommentLine("", getProject(),commenter));
            PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
          }
        }
        else {
          docAsterisk = false;
        }
      }
      else if (atLineStart instanceof PsiComment && ((PsiComment)atLineStart).getTokenType() == commenter.getBlockCommentTokenType()) {
        // Check if C-Style comment already uses asterisks.
        boolean usesAstersk = false;
        int commentLine = myDocument.getLineNumber(textRange.getStartOffset());
        if (commentLine < myDocument.getLineCount() - 1 && textRange.getEndOffset() >= myOffset) {
          int nextLineOffset = myDocument.getLineStartOffset(commentLine + 1);
          if (nextLineOffset < textRange.getEndOffset()) {
            final CharSequence chars = myDocument.getCharsSequence();
            nextLineOffset = CharArrayUtil.shiftForward(chars, nextLineOffset, " \t");
            final String commentLinePrefix = documentationCommentLinePrefix;
            usesAstersk = CharArrayUtil.regionMatches(chars, nextLineOffset, commentLinePrefix);
          }
        }
        if (usesAstersk) {
          removeTrailingSpaces(myDocument, myOffset);
          myDocument.insertString(myOffset, documentationCommentLinePrefix + " ");
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
}
