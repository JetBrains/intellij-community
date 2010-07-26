/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.*;
import com.intellij.lang.documentation.CodeDocumentationProvider;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nullable;

public class EnterHandler extends BaseEnterHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.EnterHandler");

  private final EditorActionHandler myOriginalHandler;

  public EnterHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    return myOriginalHandler.isEnabled(editor, dataContext);
  }

  public void executeWriteAction(final Editor editor, final DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
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
    final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);

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

    boolean forceIndent = false;
    Ref<Integer> caretOffsetRef = new Ref<Integer>(caretOffset);
    Ref<Integer> caretAdvanceRef = new Ref<Integer>(0);

    for(EnterHandlerDelegate delegate: Extensions.getExtensions(EnterHandlerDelegate.EP_NAME)) {
      EnterHandlerDelegate.Result result = delegate.preprocessEnter(file, editor, caretOffsetRef, caretAdvanceRef, dataContext, myOriginalHandler);
      if (result == EnterHandlerDelegate.Result.Stop) return;
      if (result != EnterHandlerDelegate.Result.Continue) {
        text = document.getCharsSequence();
        if (result == EnterHandlerDelegate.Result.DefaultForceIndent) {
          forceIndent = true;
        }
        break;
      }
    }

    caretOffset = caretOffsetRef.get().intValue();
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
    final DoEnterAction action = new DoEnterAction(file, editor, document, caretOffset, !insertSpace, caretAdvanceRef.get());
    action.setForceIndent(forceIndent);
    action.run();
  }

  private static boolean isCommentComplete(PsiComment comment, CodeDocumentationAwareCommenter commenter) {
    String commentText = comment.getText();
    final boolean docComment = isDocComment(comment, commenter);
    final String expectedCommentEnd = docComment ? commenter.getDocumentationCommentSuffix():commenter.getBlockCommentSuffix();
    if (!commentText.endsWith(expectedCommentEnd)) return false;

    final PsiFile containingFile = comment.getContainingFile();
    final Language language = comment.getParent().getLanguage();
    Lexer lexer = LanguageParserDefinitions.INSTANCE.forLanguage(language).createLexer(containingFile.getProject());
    final String commentPrefix = docComment? commenter.getDocumentationCommentPrefix() : commenter.getBlockCommentPrefix();
    lexer.start(commentText, commentPrefix == null? 0 : commentPrefix.length(), commentText.length());
    QuoteHandler fileTypeHandler = TypedHandler.getQuoteHandler(containingFile);
    JavaLikeQuoteHandler javaLikeQuoteHandler = fileTypeHandler instanceof JavaLikeQuoteHandler ?
                                                             (JavaLikeQuoteHandler)fileTypeHandler:null;

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
        if (lexer.getTokenType() == commenter.getLineCommentTokenType()) {
          lexer.start(commentText, lexer.getTokenStart() + commenter.getLineCommentPrefix().length(), commentText.length());
          lexer.advance();
          continue;
        }
        return lexer.getTokenEnd() - lexer.getTokenStart() == 1;
      }
      if (tokenType == commenter.getDocumentationCommentTokenType() || tokenType == commenter.getBlockCommentTokenType()) {
        return false;
      }
      lexer.advance();
    }
  }

  private static boolean isDocComment(final PsiElement element, final CodeDocumentationAwareCommenter commenter) {
    if (!(element instanceof PsiComment)) return false;
    PsiComment comment = (PsiComment) element;
    return commenter.isDocumentationComment(comment);
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

    public DoEnterAction(PsiFile file, Editor view, Document document, int offset, boolean insertSpace, int caretAdvance) {
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
        if (offset < 0) offset = 0;
        int lineStart = CharArrayUtil.shiftForward(chars, offset, " \t");

        final Commenter langCommenter = LanguageCommenters.INSTANCE.forLanguage(PsiUtilBase.getLanguageAtOffset(myFile, offset));
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

          if (text.equals(commenter.getDocumentationCommentPrefix()) && isDocComment(parent, commenter) ||
              text.startsWith(commenter.getDocumentationCommentPrefix()) && element instanceof PsiComment
             ) {
            PsiComment comment = isDocComment(parent, commenter) ? (PsiComment)parent:(PsiComment)element;
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
          final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(getProject());
          if (myDocument instanceof DocumentWindow) {
            DocumentWindow documentWindow = (DocumentWindow)myDocument;
            final int correctedOffset = codeStyleManager.adjustLineIndent(InjectedLanguageUtil.getTopLevelFile(myFile),
                                                                          documentWindow.injectedToHost(myOffset));
            myOffset = documentWindow.hostToInjected(correctedOffset);
          }
          else {
            myOffset = codeStyleManager.adjustLineIndent(myFile, myOffset);
          }
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

      PsiComment comment = PsiTreeUtil.getNonStrictParentOfType(myFile.findElementAt(myOffset), PsiComment.class);

      comment = createJavaDocStub(settings, comment, getProject());

      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(getProject());
      CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(getProject());
      boolean old = codeStyleSettings.ENABLE_JAVADOC_FORMATTING;
      codeStyleSettings.ENABLE_JAVADOC_FORMATTING = false;

      try {
        comment = (PsiComment)codeStyleManager.reformat(comment);
      }
      finally {
        codeStyleSettings.ENABLE_JAVADOC_FORMATTING = old;
      }
      PsiElement next = comment.getNextSibling();
      if (next == null && comment.getParent().getClass() == comment.getClass()) {
        next = comment.getParent().getNextSibling(); // expanding chameleon comment produces comment under comment
      }
      if (!(next instanceof PsiWhiteSpace) || !next.getText().contains(LINE_SEPARATOR)) {
        int lineBreakOffset = comment.getTextRange().getEndOffset();
        myDocument.insertString(lineBreakOffset, LINE_SEPARATOR);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        codeStyleManager.adjustLineIndent(myFile, lineBreakOffset + 1);
        comment = PsiTreeUtil.getNonStrictParentOfType(myFile.findElementAt(myOffset), PsiComment.class);
      }
      return comment;
    }

    private PsiComment createJavaDocStub(final CodeInsightSettings settings,
                                            final PsiComment comment,
                                            final Project project) {
      if (settings.JAVADOC_STUB_ON_ENTER) {
        final DocumentationProvider langDocumentationProvider =
          LanguageDocumentation.INSTANCE.forLanguage(comment.getParent().getLanguage());

        @Nullable final CodeDocumentationProvider docProvider;
        if (langDocumentationProvider instanceof CompositeDocumentationProvider) {
          docProvider = ((CompositeDocumentationProvider)langDocumentationProvider).getFirstCodeDocumentationProvider();
        } else {
          docProvider = langDocumentationProvider instanceof CodeDocumentationProvider ?
                                                          (CodeDocumentationProvider)langDocumentationProvider : null;
        }

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
        return PsiTreeUtil.getNonStrictParentOfType(myFile.findElementAt(myOffset), PsiComment.class);
      }
      return comment;
    }

    private Project getProject() {
      return myFile.getProject();
    }

    private static void removeTrailingSpaces(final Document document, final int startOffset) {
      int endOffset = startOffset;

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

      if (text.equals(documentationCommentLinePrefix) ||
          text.equals(docommentPrefix) ||
          text.regionMatches(lineStart - textRange.getStartOffset(), docommentPrefix, 0, docommentPrefix.length()) ||
           text.regionMatches(lineStart - textRange.getStartOffset(), documentationCommentLinePrefix, 0 , documentationCommentLinePrefix.length())
        ) {
        PsiElement element = myFile.findElementAt(myOffset);
        if (element == null) return false;

        PsiComment comment = element instanceof PsiComment ? (PsiComment)element : PsiTreeUtil.getParentOfType(element, PsiComment.class, false);
        if (comment != null) {
          int commentEnd = comment.getTextRange().getEndOffset();
          if (myOffset >= commentEnd) {
            docAsterisk = false;
          }
          else {
            removeTrailingSpaces(myDocument, myOffset);
            myDocument.insertString(myOffset, CodeDocumentationUtil.createDocCommentLine("", getProject(),commenter));
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
            usesAstersk = CharArrayUtil.regionMatches(chars, nextLineOffset, documentationCommentLinePrefix);
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
