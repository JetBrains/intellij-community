// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.tree.ParentAwareTokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.BasicJavaElementType.*;


public abstract class AbstractBasicJavaTypedHandler extends TypedHandlerDelegate {
  private boolean myJavaLTTyped;

  protected AbstractBasicJavaTypedHandler() {
  }

  protected abstract boolean isJavaFile(@NotNull PsiFile file);

  protected abstract boolean isJspFile(@NotNull PsiFile file);

  protected abstract void autoPopupMemberLookup(@NotNull Project project, @NotNull Editor editor);

  protected abstract void autoPopupJavadocLookup(@NotNull final Project project, @NotNull final Editor editor);

  protected abstract boolean isLanguageLevel5OrHigher(@NotNull PsiFile file);

  @NotNull
  protected abstract Result processWhileAndIfStatementBody(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file);


  /**
   * Automatically inserts parentheses if != or == was typed after a&b, a|b or a^b where a and b are numbers.
   *
   * @return true if the '=' char was processed
   */
  public abstract boolean handleEquality(Project project, Editor editor, PsiFile file, int offsetBefore);

  /**
   * Automatically insert parentheses around the ?: when necessary.
   *
   * @return true if question mark was handled
   */
  protected abstract boolean handleQuestionMark(Project project, Editor editor, PsiFile file, int offsetBefore);

  protected abstract boolean handleAnnotationParameter(Project project, @NotNull Editor editor, @NotNull PsiFile file);

  @NotNull
  @Override
  public Result beforeCharTyped(final char c,
                                @NotNull final Project project,
                                @NotNull final Editor editor,
                                @NotNull final PsiFile file,
                                @NotNull final FileType fileType) {
    if (!isJavaFile(file)) return Result.CONTINUE;

    if (c == '@') {
      autoPopupJavadocLookup(project, editor);
    }
    else if (c == '#' || c == '.') {
      autoPopupMemberLookup(project, editor);
    }

    int offsetBefore = editor.getCaretModel().getOffset();

    //important to calculate before inserting charTyped
    myJavaLTTyped = '<' == c &&
                    !isJspFile(file) &&
                    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET &&
                    isLanguageLevel5OrHigher(file) &&
                    TypedHandlerUtil.isAfterClassLikeIdentifierOrDot(offsetBefore, editor, JavaTokenType.DOT, JavaTokenType.IDENTIFIER,
                                                                     true);

    if ('>' == c) {
      if (!isJspFile(file) && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET && isLanguageLevel5OrHigher(file)) {
        if (TypedHandlerUtil.handleGenericGT(editor, JavaTokenType.LT, JavaTokenType.GT, JavaTypingTokenSets.INVALID_INSIDE_REFERENCE)) {
          return Result.STOP;
        }
      }
    }

    if (c == '?') {
      if (handleQuestionMark(project, editor, file, offsetBefore)) {
        return Result.STOP;
      }
    }

    if (c == '=') {
      if (handleEquality(project, editor, file, offsetBefore)) {
        return Result.STOP;
      }
    }

    if (c == ';') {
      if (handleSemicolon(project, editor, file, fileType)) return Result.STOP;
    }
    if (fileType instanceof JavaFileType && c == '}') {
      // Normal RBrace handler doesn't work with \{}, because braces in string template are not separate tokens
      int offset = editor.getCaretModel().getOffset();
      
      HighlighterIterator iterator = editor.getHighlighter().createIterator(offset-1);
      CharSequence sequence = editor.getDocument().getCharsSequence();
      if (!iterator.atEnd() && 
          (iterator.getTokenType() == JavaTokenType.STRING_TEMPLATE_BEGIN || iterator.getTokenType() == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) &&
          iterator.getEnd() == offset && sequence.subSequence(iterator.getStart(), iterator.getEnd()).toString().equals("\\{")) {
        if (sequence.length() > offset && sequence.charAt(offset) == '}') {
          editor.getCaretModel().moveToOffset(offset + 1);
          return Result.STOP;
        }
      }
    }
    if (fileType instanceof JavaFileType && c == '{') {
      int offset = editor.getCaretModel().getOffset();
      if (offset == 0) {
        return Result.CONTINUE;
      }

      HighlighterIterator iterator = editor.getHighlighter().createIterator(offset - 1);
      while (!iterator.atEnd() && iterator.getTokenType() == TokenType.WHITE_SPACE) {
        iterator.retreat();
      }
      if (iterator.atEnd() || iterator.getTokenType() == JavaTokenType.RBRACKET || iterator.getTokenType() == JavaTokenType.EQ) {
        return Result.CONTINUE;
      }
      Document doc = editor.getDocument();
      if (!iterator.atEnd() &&
          (iterator.getTokenType() == StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN ||
            iterator.getTokenType() == StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN) &&
          CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) { // "\{}" in strings
        CharSequence sequence = doc.getCharsSequence();
        if (sequence.charAt(offset - 1) == '\\' && (sequence.length() == offset || sequence.charAt(offset) != '}')) {
          doc.insertString(offset, "{}");
          editor.getCaretModel().moveToOffset(offset + 1);
          return Result.STOP;
        }
      }
      PsiDocumentManager.getInstance(project).commitDocument(doc);
      final PsiElement leaf = file.findElementAt(offset);
      if (BasicJavaAstTreeUtil.getParentOfType(leaf, BASIC_ARRAY_INITIALIZER_EXPRESSION, false,
                                               ParentAwareTokenSet.orSet(ParentAwareTokenSet.create(BASIC_CODE_BLOCK), MEMBER_SET)) != null) {
        return Result.CONTINUE;
      }
      PsiElement st = leaf != null ? leaf.getParent() : null;
      PsiElement prev = offset > 1 ? file.findElementAt(offset - 1) : null;
      if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET && isRparenth(leaf) &&
          st != null &&
          (BasicJavaAstTreeUtil.is(st.getNode(), BASIC_WHILE_STATEMENT) ||
           BasicJavaAstTreeUtil.is(st.getNode(), BASIC_IF_STATEMENT)) &&
          shouldInsertStatementBody(st, doc, prev)) {
        return processWhileAndIfStatementBody(project, editor, file);
      }

      if (BasicJavaAstTreeUtil.getParentOfType(leaf, BASIC_CODE_BLOCK, false, MEMBER_SET) != null &&
          !shouldInsertPairedBrace(leaf)) {
        EditorModificationUtilEx.insertStringAtCaret(editor, "{");
        TypedHandler.indentOpenedBrace(project, editor);
        return Result.STOP; // use case: manually wrapping part of method's code in 'if', 'while', etc
      }
    }

    return Result.CONTINUE;
  }

  private static boolean shouldInsertPairedBrace(@NotNull PsiElement leaf) {
    PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf(leaf);
    // lambda
    if (prevLeaf != null && prevLeaf.getNode().getElementType() == JavaTokenType.ARROW) return true;
    // anonymous class
    ParentAwareTokenSet stopAt = ParentAwareTokenSet.orSet(MEMBER_SET, ParentAwareTokenSet.create(BASIC_CODE_BLOCK));
    if (BasicJavaAstTreeUtil.getParentOfType(prevLeaf, BASIC_NEW_EXPRESSION, true, stopAt) != null) return true;
    // local class
    if (prevLeaf != null && prevLeaf.getParent() != null && BasicJavaAstTreeUtil.is(prevLeaf.getNode(), JavaTokenType.IDENTIFIER) &&
        BasicJavaAstTreeUtil.is(prevLeaf.getParent().getNode(), CLASS_SET)) {
      return true;
    }
    // local record
    if (prevLeaf != null && prevLeaf.getParent() != null && prevLeaf.getNode().getElementType() == JavaTokenType.RPARENTH &&
        BasicJavaAstTreeUtil.is(prevLeaf.getParent().getNode(), BASIC_RECORD_HEADER)) {
      return true;
    }
    return false;
  }

  private static boolean shouldInsertStatementBody(@NotNull PsiElement statement, @NotNull Document doc, @Nullable PsiElement prev) {

    ASTNode block;
    ASTNode astNodeStatement = statement.getNode();
    if (BasicJavaAstTreeUtil.is(astNodeStatement, BASIC_WHILE_STATEMENT)) {
      block = BasicJavaAstTreeUtil.getBlock(astNodeStatement);
    }
    else {
      block = BasicJavaAstTreeUtil.getThenBranch(astNodeStatement);
    }
    ASTNode condition = BasicJavaAstTreeUtil.findChildByType(astNodeStatement, EXPRESSION_SET);
    ASTNode latestExpression = BasicJavaAstTreeUtil.getParentOfType(BasicJavaAstTreeUtil.toNode(prev), EXPRESSION_SET);
    if (BasicJavaAstTreeUtil.is(latestExpression, BASIC_NEW_EXPRESSION) &&
        BasicJavaAstTreeUtil.getAnonymousClass(latestExpression) == null) {
      return false;
    }
    return !(BasicJavaAstTreeUtil.is(block, BASIC_BLOCK_STATEMENT)) &&
           (block == null || startLine(doc, block) != startLine(doc, astNodeStatement) || condition == null);
  }

  private static boolean isRparenth(@Nullable PsiElement leaf) {
    if (leaf == null) return false;
    if (leaf.getNode().getElementType() == JavaTokenType.RPARENTH) return true;
    PsiElement next = PsiTreeUtil.nextVisibleLeaf(leaf);
    if (next == null) return false;
    return next.getNode().getElementType() == JavaTokenType.RPARENTH;
  }

  private static int startLine(@NotNull Document doc, @NotNull ASTNode astNode) {
    return doc.getLineNumber(astNode.getTextRange().getStartOffset());
  }

  @NotNull
  @Override
  public Result charTyped(final char c, @NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    if (!(isJavaFile(file))) return Result.CONTINUE;

    if (myJavaLTTyped) {
      myJavaLTTyped = false;
      TypedHandlerUtil.handleAfterGenericLT(editor, JavaTokenType.LT, JavaTokenType.GT, JavaTypingTokenSets.INVALID_INSIDE_REFERENCE);
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
    else if (c == '.') {
      if (handleDotTyped(project, editor, file)) {
        return Result.STOP;
      }
    }
    return Result.CONTINUE;
  }

  private static boolean handleDotTyped(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    int offset = editor.getCaretModel().getOffset() - 1;
    if (offset >= 0) {
      Document document = editor.getDocument();
      int line = document.getLineNumber(offset);
      int lineStart = document.getLineStartOffset(line);
      if (StringUtil.isEmptyOrSpaces(document.getCharsSequence().subSequence(lineStart, offset))) {
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);
        return true;
      }
    }
    return false;
  }

  private static boolean handleSemicolon(@NotNull Project project,
                                  @NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @NotNull FileType fileType) {
    if (!(fileType instanceof JavaFileType)) return false;
    int offset = editor.getCaretModel().getOffset();
    if (offset == editor.getDocument().getTextLength()) return false;

    if (moveSemicolonAtRParen(project, editor, file, offset)) return true;

    char charAt = editor.getDocument().getCharsSequence().charAt(offset);
    if (charAt != ';') return false;

    HighlighterIterator hi = editor.getHighlighter().createIterator(offset);
    if (hi.atEnd() || hi.getTokenType() != JavaTokenType.SEMICOLON) return false;

    EditorModificationUtil.moveCaretRelatively(editor, 1);
    return true;
  }

  private static boolean moveSemicolonAtRParen(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, int caretOffset) {
    if (!Registry.is("editor.move.semicolon.after.paren")) {
      return false;
    }

    ApplicationManager.getApplication().assertWriteAccessAllowed();

    // Note, this feature may be rewritten using only lexer if needed.
    // In that case accuracy will not be 100%, but good enough.

    HighlighterIterator it = editor.getHighlighter().createIterator(caretOffset);
    int afterLastParenOffset = -1;

    while (!it.atEnd()) {
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

    if (!it.atEnd() && afterLastParenOffset >= 0 && afterLastParenOffset >= caretOffset) {
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      PsiElement curElement = file.findElementAt(caretOffset);
      ASTNode curStmt = BasicJavaAstTreeUtil.getParentOfType(BasicJavaAstTreeUtil.toNode(curElement), STATEMENT_SET);
      if (curStmt != null) {
        if (BasicJavaAstTreeUtil.is(curStmt, BASIC_TRY_STATEMENT)) {
          // try-with-resources can contain semicolons inside
          return false;
        }
        if (BasicJavaAstTreeUtil.is(curStmt, BASIC_FOR_STATEMENT)) {
          // for loop can have semicolons inside
          return false;
        }
        // It may worth to check if the error element is about expecting semicolon
        PsiElement curPsiElement = BasicJavaAstTreeUtil.toPsi(curStmt);
        if (curPsiElement != null && PsiTreeUtil.getDeepestLast(curPsiElement) instanceof PsiErrorElement) {
          int stmtEndOffset = curStmt.getTextRange().getEndOffset();
          if (stmtEndOffset == afterLastParenOffset || stmtEndOffset == it.getStart()) {
            editor.getDocument().insertString(stmtEndOffset, ";");
            editor.getCaretModel().moveToOffset(stmtEndOffset + 1);
            return true;
          }
        }
      }
    }

    return false;
  }

  private static boolean isAtLineEnd(HighlighterIterator it) {
    if (it.getTokenType() == TokenType.WHITE_SPACE) {
      CharSequence tokenText = it.getDocument().getImmutableCharSequence().subSequence(it.getStart(), it.getEnd());
      return CharArrayUtil.containLineBreaks(tokenText);
    }
    return false;
  }

  private static boolean autoIndentCase(Editor editor, Project project, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    PsiElement currElement = file.findElementAt(offset - 1);
    if (currElement != null) {
      PsiElement parent = currElement.getParent();
      if (BasicJavaAstTreeUtil.is(BasicJavaAstTreeUtil.toNode(parent), BASIC_SWITCH_LABEL_STATEMENT)) {
        CodeStyleManager.getInstance(project).adjustLineIndent(file, parent.getTextOffset());
        return true;
      }
    }
    return false;
  }
}
