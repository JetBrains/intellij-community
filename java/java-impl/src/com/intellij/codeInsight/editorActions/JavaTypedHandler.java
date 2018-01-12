/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.JavaClassReferenceCompletionContributor;
import com.intellij.codeInsight.editorActions.smartEnter.JavaSmartEnterProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.PsiErrorElementUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class JavaTypedHandler extends TypedHandlerDelegate {
  static final TokenSet INVALID_INSIDE_REFERENCE = TokenSet.create(JavaTokenType.SEMICOLON, JavaTokenType.LBRACE, JavaTokenType.RBRACE);
  private boolean myJavaLTTyped;

  private static void autoPopupMemberLookup(Project project, final Editor editor) {
    AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, file -> {
      int offset = editor.getCaretModel().getOffset();

      PsiElement lastElement = file.findElementAt(offset - 1);
      if (lastElement == null) {
        return false;
      }

      //do not show lookup when typing varargs ellipsis
      final PsiElement prevSibling = PsiTreeUtil.prevVisibleLeaf(lastElement);
      if (prevSibling == null || ".".equals(prevSibling.getText())) return false;
      PsiElement parent = prevSibling;
      do {
        parent = parent.getParent();
      } while(parent instanceof PsiJavaCodeReferenceElement || parent instanceof PsiTypeElement);
      if (parent instanceof PsiParameterList || parent instanceof PsiParameter) return false;

      if (!".".equals(lastElement.getText()) && !"#".equals(lastElement.getText())) {
        return JavaClassReferenceCompletionContributor.findJavaClassReference(file, offset - 1) != null;
      }
      else{
        final PsiElement element = file.findElementAt(offset);
        return element == null ||
               !"#".equals(lastElement.getText()) ||
               PsiTreeUtil.getParentOfType(element, PsiDocComment.class) != null;
      }
    });
  }

  @NotNull
  @Override
  public Result beforeCharTyped(final char c, @NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file, @NotNull final FileType fileType) {
    if (!(file instanceof PsiJavaFile)) return Result.CONTINUE;

    if (c == '@') {
      autoPopupJavadocLookup(project, editor);
    }
    else if (c == '#' || c == '.') {
      autoPopupMemberLookup(project, editor);
    }

    int offsetBefore = editor.getCaretModel().getOffset();

    //important to calculate before inserting charTyped
    myJavaLTTyped = '<' == c &&
                    !(file instanceof JspFile) &&
                    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
                    PsiUtil.isLanguageLevel5OrHigher(file) &&
                    TypedHandlerUtil.isAfterClassLikeIdentifierOrDot(offsetBefore, editor, JavaTokenType.DOT, JavaTokenType.IDENTIFIER, true);

    if ('>' == c) {
      if (!(file instanceof JspFile) && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET && PsiUtil.isLanguageLevel5OrHigher(file)) {
        if (TypedHandlerUtil.handleGenericGT(editor, JavaTokenType.LT, JavaTokenType.GT, INVALID_INSIDE_REFERENCE)) return Result.STOP;
      }
    }

    if (c == ';') {
      if (handleSemicolon(editor, file, fileType)) return Result.STOP;
    }
    if (fileType == StdFileTypes.JAVA && c == '{') {
      int offset = editor.getCaretModel().getOffset();
      if (offset == 0) {
        return Result.CONTINUE;
      }

      HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(offset - 1);
      while (!iterator.atEnd() && iterator.getTokenType() == TokenType.WHITE_SPACE) {
        iterator.retreat();
      }
      if (iterator.atEnd() || iterator.getTokenType() == JavaTokenType.RBRACKET || iterator.getTokenType() == JavaTokenType.EQ) {
        return Result.CONTINUE;
      }
      Document doc = editor.getDocument();
      PsiDocumentManager.getInstance(project).commitDocument(doc);
      final PsiElement leaf = file.findElementAt(offset);
      if (PsiTreeUtil.getParentOfType(leaf, PsiArrayInitializerExpression.class, false, PsiCodeBlock.class, PsiMember.class) != null) {
        return Result.CONTINUE;
      }
      PsiElement st = leaf != null ? leaf.getParent() : null;
      PsiElement prev = offset > 1 ? file.findElementAt(offset - 1) : null;
      if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET && isRparenth(leaf) &&
          (st instanceof PsiWhileStatement || st instanceof PsiIfStatement) && shouldInsertStatementBody(st, doc, prev)) {
        CommandProcessor.getInstance().executeCommand(project, () -> new JavaSmartEnterProcessor().process(project, editor, file), "Insert block statement", null);
        return Result.STOP;
      }

      PsiElement prevLeaf = leaf == null ? null : PsiTreeUtil.prevVisibleLeaf(leaf);
      if (PsiUtil.isJavaToken(prevLeaf, JavaTokenType.ARROW) || 
          PsiTreeUtil.getParentOfType(prevLeaf, PsiNewExpression.class, true, PsiCodeBlock.class, PsiMember.class) != null) {
        return Result.CONTINUE;
      }

      if (PsiTreeUtil.getParentOfType(leaf, PsiCodeBlock.class, false, PsiMember.class) != null) {
        EditorModificationUtil.insertStringAtCaret(editor, "{");
        TypedHandler.indentOpenedBrace(project, editor);
        return Result.STOP; // use case: manually wrapping part of method's code in 'if', 'while', etc
      }
    }

    return Result.CONTINUE;
  }

  private static boolean shouldInsertStatementBody(@NotNull PsiElement statement, @NotNull Document doc, @Nullable PsiElement prev) {
    PsiStatement block = statement instanceof PsiWhileStatement ? ((PsiWhileStatement)statement).getBody() : ((PsiIfStatement)statement).getThenBranch();
    PsiExpression condition = PsiTreeUtil.getChildOfType(statement, PsiExpression.class);
    PsiExpression latestExpression = PsiTreeUtil.getParentOfType(prev, PsiExpression.class);
    if (latestExpression instanceof PsiNewExpression && ((PsiNewExpression)latestExpression).getAnonymousClass() == null) return false;
    return !(block instanceof PsiBlockStatement) && (block == null || startLine(doc, block) != startLine(doc, statement) || condition == null);
  }

  private static boolean isRparenth(@Nullable PsiElement leaf) {
    if (leaf == null) return false;
    if (leaf.getNode().getElementType() == JavaTokenType.RPARENTH) return true;
    PsiElement next = PsiTreeUtil.nextVisibleLeaf(leaf);
    if (next == null) return false;
    return next.getNode().getElementType() == JavaTokenType.RPARENTH;
  }

  private static int startLine(@NotNull Document doc, @NotNull PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }

  @NotNull
  @Override
  public Result charTyped(final char c, @NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    if (myJavaLTTyped) {
      myJavaLTTyped = false;
      TypedHandlerUtil.handleAfterGenericLT(editor, JavaTokenType.LT, JavaTokenType.GT, INVALID_INSIDE_REFERENCE);
      return Result.STOP;
    }
    else if (c == ':') {
      if (autoIndentCase(editor, project, file)) {
        return Result.STOP;
      }
    }
    else if (c == ',' && handleAnnotationParameter(project, editor, file)) {
      return Result.STOP;
    }
    return Result.CONTINUE;
  }

  private static boolean handleAnnotationParameter(Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    int caret = editor.getCaretModel().getOffset();
    if (mightBeInsideDefaultAnnotationAttribute(editor, caret - 2)) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      PsiAnnotation anno = PsiTreeUtil.getParentOfType(file.findElementAt(caret), PsiAnnotation.class, false, PsiExpression.class, PsiComment.class);
      PsiNameValuePair attr = anno == null ? null : getTheOnlyDefaultAttribute(anno);
      if (attr != null && hasDefaultArrayMethod(anno) && !(attr.getValue() instanceof PsiArrayInitializerMemberValue)) {
        editor.getDocument().insertString(caret, "}");
        editor.getDocument().insertString(attr.getTextRange().getStartOffset(), "{");
        return true;
      }
    }
    return false;
  }

  @Nullable private static PsiNameValuePair getTheOnlyDefaultAttribute(@NotNull PsiAnnotation anno) {
    List<PsiNameValuePair> attributes = ContainerUtil.findAll(anno.getParameterList().getAttributes(), a -> !a.getTextRange().isEmpty());
    return attributes.size() == 1 && attributes.get(0).getNameIdentifier() == null ? attributes.get(0) : null;
  }

  private static boolean hasDefaultArrayMethod(@NotNull PsiAnnotation anno) {
    PsiJavaCodeReferenceElement nameRef = anno.getNameReferenceElement();
    PsiElement annoClass = nameRef == null ? null : nameRef.resolve();
    if (annoClass instanceof PsiClass) {
      PsiMethod[] methods = ((PsiClass)annoClass).getMethods();
      return methods.length == 1 && PsiUtil.isAnnotationMethod(methods[0]) &&
             PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(methods[0].getName()) &&
             methods[0].getReturnType() instanceof PsiArrayType;
    }
    return false;
  }

  private static boolean mightBeInsideDefaultAnnotationAttribute(@NotNull Editor editor, int offset) {
    if (offset < 0) return false;
    HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    int parenCount = 0;
    while (!iterator.atEnd()) {
      IElementType tokenType = iterator.getTokenType();
      if (tokenType == JavaTokenType.AT) {
        return true;
      }
      if (tokenType == JavaTokenType.RPARENTH || tokenType == JavaTokenType.LBRACE ||
          tokenType == JavaTokenType.EQ || tokenType == JavaTokenType.SEMICOLON || tokenType == JavaTokenType.COMMA) {
        return false;
      }
      if (tokenType == JavaTokenType.LPARENTH && ++parenCount > 1) {
        return false;
      }
      iterator.retreat();
    }
    return false;
  }

  private static boolean handleSemicolon(@NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
    if (fileType != StdFileTypes.JAVA) return false;
    int offset = editor.getCaretModel().getOffset();
    if (offset == editor.getDocument().getTextLength()) return false;

    if (moveSemicolonAtRParen(editor, file, offset)) return true;

    char charAt = editor.getDocument().getCharsSequence().charAt(offset);
    if (charAt != ';') return false;

    HighlighterIterator hi = ((EditorEx)editor).getHighlighter().createIterator(offset);
    if (hi.atEnd() || hi.getTokenType() != JavaTokenType.SEMICOLON) return false;

    EditorModificationUtil.moveCaretRelatively(editor, 1);
    return true;
  }

  private static boolean moveSemicolonAtRParen(@NotNull Editor editor, @NotNull PsiFile file, int caretOffset) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    // Disable feature if code model is not synchronized with the document.
    // Note: this feature can be implemented using only lexer because there
    // are not so many statements which allow semicolon inside them before
    // closing paren. Therefore, in other cases, if user types semicolon
    // before rparen, that usually means that he wants to end the statement.
    if (PsiDocumentManager.getInstance(file.getProject()).isUncommited(editor.getDocument())) {
      // But enable in unit tests to avoid commiting document after each typing
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        return false;
      }
    }

    HighlighterIterator it = ((EditorEx)editor).getHighlighter().createIterator(caretOffset);
    int afterLastParenOffset = -1;

    try {
      do {
        if (isAtLineEnd(it)) {
          break;
        }
        else if (it.getTokenType() == JavaTokenType.RBRACE) {
          break;
        }
        else if (it.getTokenType() == JavaTokenType.RPARENTH) {
          afterLastParenOffset = it.getEnd();
        }
        else if (it.getTokenType() != TokenType.WHITE_SPACE) {
          // Other tokens are not permitted
          return false;
        }
        it.advance();
      }
      while (!it.atEnd());
    }
    catch (IndexOutOfBoundsException ex) {
      // May be thrown when checking character at the current offset.
      return false;
    }

    if (!it.atEnd() && afterLastParenOffset >= 0 && afterLastParenOffset >= caretOffset) {
      PsiElement curElement = file.findElementAt(caretOffset);
      PsiStatement curStmt = PsiTreeUtil.getParentOfType(curElement, PsiStatement.class);
      if (curStmt != null) {
        if (curStmt instanceof PsiTryStatement) {
          // try-with-resources can contain semicolons inside
          return false;
        }
        if (curStmt instanceof PsiForStatement) {
          // for loop can have semicolons inside
          return false;
        }
        // It may worth to check if the error element is about expecting semicolon
        if (PsiTreeUtil.getDeepestLast(curStmt) instanceof PsiErrorElement) {
          int stmtEndOffset = curStmt.getTextRange().getEndOffset();
          if (stmtEndOffset == afterLastParenOffset || stmtEndOffset == it.getStart()) {
            editor.getDocument().insertString(stmtEndOffset, ";");
            EditorModificationUtil.moveCaretRelatively(editor, stmtEndOffset - caretOffset + 1);
            return true;
          }
        }
      }
    }

    return false;
  }

  private static boolean isAtLineEnd(HighlighterIterator it) {
    if (it.getTokenType() == TokenType.WHITE_SPACE) {
      for (int offset = it.getStart(); offset < it.getEnd(); ++offset) {
        char chr = it.getDocument().getCharsSequence().charAt(offset);
        if (chr == '\n') {
          return true;
        }
      }
    }
    return false;
  }

  private static void autoPopupJavadocLookup(final Project project, final Editor editor) {
    AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, file -> {
      int offset = editor.getCaretModel().getOffset();

      PsiElement lastElement = file.findElementAt(offset - 1);
      return lastElement != null && StringUtil.endsWithChar(lastElement.getText(), '@');
    });
  }
  
  private static boolean autoIndentCase(Editor editor, Project project, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    PsiElement currElement = file.findElementAt(offset - 1);
    if (currElement != null) {
      PsiElement parent = currElement.getParent();
      if (parent instanceof PsiSwitchLabelStatement) {
        CodeStyleManager.getInstance(project).adjustLineIndent(file, parent.getTextOffset());
        return true;
      }
    }
    return false;
  }

  /**
   * needed for API compatibility only
   * @deprecated Please use {@link TypedHandlerUtil#handleGenericGT} instead
   */
  @Deprecated
  public static boolean handleJavaGT(@NotNull final Editor editor,
                                     @NotNull final IElementType lt,
                                     @NotNull final IElementType gt,
                                     @NotNull final TokenSet invalidInsideReference) {
    return TypedHandlerUtil.handleGenericGT(editor, lt, gt, invalidInsideReference);
  }

  /**
   * needed for API compatibility only
   * @deprecated Please use {@link TypedHandlerUtil#handleAfterGenericLT} instead
   */
  @Deprecated
  public static void handleAfterJavaLT(@NotNull final Editor editor,
                                       @NotNull final IElementType lt,
                                       @NotNull final IElementType gt,
                                       @NotNull final TokenSet invalidInsideReference) {
    TypedHandlerUtil.handleAfterGenericLT(editor, lt, gt, invalidInsideReference);
  }

  /**
   * needed for API compatibility only
   * @deprecated Please use {@link TypedHandlerUtil#isClassLikeIdentifier} instead
   */
  @Deprecated
  public static boolean isClassLikeIdentifier(int offset,
                                              @NotNull Editor editor,
                                              @NotNull HighlighterIterator iterator,
                                              @NotNull final IElementType idType) {
    return TypedHandlerUtil.isClassLikeIdentifier(offset, editor, iterator, idType);
  }
}
