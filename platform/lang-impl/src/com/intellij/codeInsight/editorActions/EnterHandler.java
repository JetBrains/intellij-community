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
import com.intellij.ide.DataManager;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.LanguageParserDefinitions;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
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
    if (project != null && !project.isDefault()) {
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
      if (caretOffsetRef.get() > document.getTextLength()) {
        throw new AssertionError("Wrong caret offset change by " + delegate);
      }

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
      !isFirstColumn && !(caretOffset >= text.length() || text.charAt(caretOffset) == ' ' || text.charAt(caretOffset) == '\t');
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
    final DoEnterAction action = new DoEnterAction(
      file, editor, document, dataContext, caretOffset, !insertSpace, caretAdvanceRef.get(), project
    );
    action.setForceIndent(forceIndent);
    action.run();
    PsiDocumentManager.getInstance(project).commitDocument(document);
  }

  private static boolean isCommentComplete(PsiComment comment, CodeDocumentationAwareCommenter commenter, Editor editor) {
    for (CommentCompleteHandler handler : Extensions.getExtensions(CommentCompleteHandler.EP_NAME)) {
      if (handler.isApplicable(comment, commenter)) {
        return handler.isCommentComplete(comment, commenter);
      }
    }

    String commentText = comment.getText();
    final boolean docComment = isDocComment(comment, commenter);
    final String expectedCommentEnd = docComment ? commenter.getDocumentationCommentSuffix():commenter.getBlockCommentSuffix();
    if (!commentText.endsWith(expectedCommentEnd)) return false;

    final PsiFile containingFile = comment.getContainingFile();
    final Language language = comment.getParent().getLanguage();
    Lexer lexer = LanguageParserDefinitions.INSTANCE.forLanguage(language).createLexer(containingFile.getProject());
    final String commentPrefix = docComment? commenter.getDocumentationCommentPrefix() : commenter.getBlockCommentPrefix();
    lexer.start(commentText, commentPrefix == null? 0 : commentPrefix.length(), commentText.length());
    QuoteHandler fileTypeHandler = TypedHandler.getQuoteHandler(containingFile, editor);
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
        else if (isInvalidPsi(comment)) {
          return false;
        }
        return lexer.getTokenEnd() - lexer.getTokenStart() == 1;
      }
      if (tokenType == commenter.getDocumentationCommentTokenType() || tokenType == commenter.getBlockCommentTokenType()) {
        return false;
      }
      lexer.advance();
    }
  }

  /**
   * There is a following possible use-case:
   * <pre>
   * <ul>
   *   <li>
   *     <b>Particular document has valid text:</b>
   *     <pre>
   *       [caret]
   *       class A {
   *           int foo() {
   *             return 1 *&#47;*comment*&#47; 1;
   *           }
   *       }
   *     </pre>
   *   </li>
   *   <li>
   *     <b>The user starts comment (inserts comment start symbols):</b>
   *     <pre>
   *       &#47;**[caret]
   *       class A {
   *           int foo() {
   *             return 1 *&#47;*comment*&#47; 1;
   *           }
   *       }
   *     </pre>
   *   </li>
   *   <li>The user presses <code>'enter'</code>;</li>
   * </ul>
   * </pre>
   * We want to understand that doc comment is incomplete now, i.e. don't want to consider '*&#47;' before
   * '*comment*&#47; 1;' as comment end. Current approach is to check if next PSI sibling to the current PSI comment is invalid.
   * This method allows to perform such an examination.
   */
  private static boolean isInvalidPsi(@NotNull PsiElement base) {
    for (PsiElement current = base.getNextSibling(); current != null; current = current.getNextSibling()) {
      if (current.getTextLength() != 0) {
        return current instanceof PsiErrorElement;
      }
    }
    return false;
  }
  
  private static boolean isDocComment(final PsiElement element, final CodeDocumentationAwareCommenter commenter) {
    if (!(element instanceof PsiComment)) return false;
    PsiComment comment = (PsiComment) element;
    return commenter.isDocumentationComment(comment);
  }

  private static class DoEnterAction implements Runnable {
    
    private final DataContext myDataContext;
    private final PsiFile myFile;
    private int myOffset;
    private final Document myDocument;
    private final boolean myInsertSpace;
    private final Editor myEditor;
    private final Project myProject;
    private int myCaretAdvance;

    private boolean myForceIndent = false;
    private static final String LINE_SEPARATOR = "\n";

    public DoEnterAction(PsiFile file, Editor view, Document document, DataContext dataContext, int offset, boolean insertSpace,
                         int caretAdvance, Project project) 
    {
      myEditor = view;
      myFile = file;
      myDataContext = dataContext;
      myOffset = offset;
      myDocument = document;
      myInsertSpace = insertSpace;
      myCaretAdvance = caretAdvance;
      myProject = project;
    }

    public void setForceIndent(boolean forceIndent) {
      myForceIndent = forceIndent;
    }

    public void run() {
      CaretModel caretModel = myEditor.getCaretModel();
      try {
        final CharSequence chars = myDocument.getCharsSequence();
        int i = CharArrayUtil.shiftBackwardUntil(chars, myOffset - 1, LINE_SEPARATOR) - 1;
        i = CharArrayUtil.shiftBackwardUntil(chars, i, LINE_SEPARATOR) + 1;
        if (i < 0) i = 0;
        int lineStart = CharArrayUtil.shiftForward(chars, i, " \t");
        CodeDocumentationUtil.CommentContext commentContext 
          = CodeDocumentationUtil.tryParseCommentContext(myFile, chars, myOffset, lineStart);

        if (commentContext.docStart) {
          PsiElement element = myFile.findElementAt(commentContext.lineStart);
          final String text = element.getText();
          final PsiElement parent = element.getParent();

          if (text.equals(commentContext.commenter.getDocumentationCommentPrefix()) && isDocComment(parent, commentContext.commenter) ||
              text.startsWith(commentContext.commenter.getDocumentationCommentPrefix()) && element instanceof PsiComment)
          {
            PsiComment comment = isDocComment(parent, commentContext.commenter) ? (PsiComment)parent:(PsiComment)element;
            int commentEnd = comment.getTextRange().getEndOffset();

            if (myOffset >= commentEnd) {
              commentContext.docStart = false;
            }
            else {
              if (isCommentComplete(comment, commentContext.commenter, myEditor)) {
                if (myOffset >= commentEnd) {
                  commentContext.docAsterisk = false;
                  commentContext.docStart = false;
                }
                else {
                  commentContext.docAsterisk = true;
                  commentContext.docStart = false;
                }
              }
              else {
                generateJavadoc(commentContext.commenter);
              }
            }
          }
          else {
            commentContext.docStart = false;
          }
        }
        else if (commentContext.cStyleStart) {
          PsiElement element = myFile.findElementAt(commentContext.lineStart);
          if (element instanceof PsiComment && commentContext.commenter.getBlockCommentTokenType() == ((PsiComment)element).getTokenType()) {
            final PsiComment comment = (PsiComment)element;
            int commentEnd = comment.getTextRange().getEndOffset();
            if (myOffset >= commentEnd) {
              commentContext.docStart = false;
            }
            else {
              if (isCommentComplete(comment, commentContext.commenter, myEditor)) {
                if (myOffset >= commentEnd) {
                  commentContext.docAsterisk = false;
                  commentContext.docStart = false;
                }
                else {
                  commentContext.docAsterisk = true;
                  commentContext.docStart = false;
                }
              }
              else {
                final int currentEndOfLine = CharArrayUtil.shiftForwardUntil(chars, myOffset, "\n");
                myDocument.insertString(currentEndOfLine, " " + commentContext.commenter.getBlockCommentSuffix());
                int lstart = CharArrayUtil.shiftBackwardUntil(chars, myOffset, "\n");
                myDocument.insertString(currentEndOfLine, chars.subSequence(lstart, myOffset));
              }
            }
          }
          else {
            commentContext.docStart = false;
          }
        }

        String indentInsideJavadoc = null;
        int line = myDocument.getLineNumber(myOffset);
        if (line > 0 && (commentContext.docAsterisk || commentContext.docStart)) {
          indentInsideJavadoc = CodeDocumentationUtil.getIndentInsideJavadoc(myDocument, myDocument.getLineStartOffset(line - 1));
        }
        
        if (commentContext.docAsterisk) {
          commentContext.docAsterisk = insertDocAsterisk(commentContext.lineStart, commentContext.docAsterisk,
                                                         !StringUtil.isEmpty(indentInsideJavadoc), commentContext.commenter);
        }

        boolean docIndentApplied = false;
        CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
        if (codeInsightSettings.SMART_INDENT_ON_ENTER || myForceIndent || commentContext.docStart || commentContext.docAsterisk
            || commentContext.slashSlash) 
        {
          final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(getProject());
          myOffset = codeStyleManager.adjustLineIndent(myFile, myOffset);
          PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
          
          if (!StringUtil.isEmpty(indentInsideJavadoc) && myOffset < myDocument.getTextLength()) {
            myDocument.insertString(myOffset + 1, indentInsideJavadoc);
            myOffset += indentInsideJavadoc.length();
            docIndentApplied = true;
          }

          if (myForceIndent && indentInsideJavadoc != null) {
            int indentSize = CodeStyleSettingsManager.getSettings(myProject).getIndentSize(myFile.getFileType());
            myDocument.insertString(myOffset + 1, StringUtil.repeatSymbol(' ', indentSize));
            myCaretAdvance += indentSize;
          }
        }

        if ((commentContext.docAsterisk || commentContext.docStart || commentContext.slashSlash) && !docIndentApplied) {
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

        if ((commentContext.docAsterisk || commentContext.slashSlash) && !commentContext.docStart) {
          myCaretAdvance += commentContext.slashSlash ? commentContext.commenter.getLineCommentPrefix().length() : 1;
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

      caretModel.moveToOffset(myOffset);
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      myEditor.getSelectionModel().removeSelection();
      if (myCaretAdvance != 0) {
        LogicalPosition caretPosition = caretModel.getLogicalPosition();
        LogicalPosition pos = new LogicalPosition(caretPosition.line, caretPosition.column + myCaretAdvance);
        caretModel.moveToLogicalPosition(pos);
      }
    }

    private void generateJavadoc(CodeDocumentationAwareCommenter commenter) throws IncorrectOperationException {
      CodeInsightSettings settings = CodeInsightSettings.getInstance();
      StringBuilder buffer = new StringBuilder();
      final String docCommentLinePrefix = commenter.getDocumentationCommentLinePrefix();
      if(docCommentLinePrefix==null){
        return;
      }
      
      // There are at least two approaches for completing javadoc in case there is a text between current caret position and line end:
      //     1. Move that tail text below the javadoc. Use-case:
      //         Before:
      //             /**<caret>public void foo() {}
      //         After:
      //             /**
      //              */
      //             public void foo() {}
      //     2. Move the tail text inside the javadoc. Use-case:
      //          Before:
      //             /**This is <caret>javadoc description
      //          After:
      //             /** This is
      //              * javadoc description
      //              */
      // The later is most relevant when we have 'auto wrap when typing reaches right margin' option set, i.e. user starts javadoc
      // and types until right margin is reached. We want the wrapped text tail to be located inside javadoc and continue typing
      // inside it. So, we have a control flow branch below that does the trick.
      buffer.append(docCommentLinePrefix);
      if (DataManager.getInstance().loadFromDataContext(myDataContext, AutoHardWrapHandler.AUTO_WRAP_LINE_IN_PROGRESS_KEY) == Boolean.TRUE) {
        myDocument.insertString(myOffset, buffer);

        // We create new buffer here because the one referenced by current 'buffer' variable value may be already referenced at another
        // place (e.g. 'undo' processing stuff).
        buffer = new StringBuilder(LINE_SEPARATOR).append(commenter.getDocumentationCommentSuffix());
        int line = myDocument.getLineNumber(myOffset);
        myOffset = myDocument.getLineEndOffset(line);
      }
      else {
        buffer.append(LINE_SEPARATOR);
        buffer.append(commenter.getDocumentationCommentSuffix());
      }
      
      PsiComment comment = createComment(buffer, settings);
      if(comment == null){
        return;
      }
      
      myOffset = comment.getTextRange().getStartOffset();
      CharSequence text = myDocument.getCharsSequence();
      myOffset = CharArrayUtil.shiftForwardUntil(text, myOffset, LINE_SEPARATOR);
      myOffset = CharArrayUtil.shiftForward(text, myOffset, LINE_SEPARATOR);
      myOffset = CharArrayUtil.shiftForwardUntil(text, myOffset, docCommentLinePrefix) + 1;
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
    private PsiComment createComment(final CharSequence buffer, final CodeInsightSettings settings)
      throws IncorrectOperationException {
      myDocument.insertString(myOffset, buffer);

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

    private boolean insertDocAsterisk(int lineStart, boolean docAsterisk, boolean previousLineIndentUsed,
                                      CodeDocumentationAwareCommenter commenter) 
    {
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
            String toInsert = previousLineIndentUsed ? "*" : CodeDocumentationUtil.createDocCommentLine("", getProject(), commenter);
            myDocument.insertString(myOffset, toInsert);
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
