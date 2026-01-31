// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.smartEnter.JavaSmartEnterProcessor;
import com.intellij.core.JavaPsiBundle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.AbstractBasicJavaFile;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class JavaTypedHandlerBase extends TypedHandlerDelegate {
  private boolean myJavaLTTyped;

  protected JavaTypedHandlerBase() {
  }

  private static boolean isJavaFile(@NotNull PsiFile file) {
    return file instanceof AbstractBasicJavaFile;
  }

  private static boolean isJspFile(@NotNull PsiFile file) {
    // avoid dependency on jsp openapi until we have xml psi on
    return file.getLanguage() instanceof XMLLanguage;
  }

  protected void autoPopupMemberLookup(@NotNull Project project, @NotNull Editor editor) {

  }

  protected void autoPopupJavadocLookup(final @NotNull Project project, final @NotNull Editor editor) {

  }

  private static boolean isLanguageLevel5OrHigher(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  private static @NotNull Result processWhileAndIfStatementBody(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    CommandProcessor.getInstance().executeCommand(project, () -> new JavaSmartEnterProcessor().process(project, editor, file),
                                                  JavaPsiBundle.message("command.name.insert.block.statement"), null);
    return Result.STOP;
  }


  /**
   * Automatically inserts parentheses if != or == was typed after a&b, a|b or a^b where a and b are numbers.
   *
   * @return true if the '=' char was processed
   */
  public boolean handleEquality(Project project, Editor editor, PsiFile file, int offsetBefore) {
    return false;
  }

  /**
   * Automatically insert parentheses around the ?: when necessary.
   *
   * @return true if question mark was handled
   */
  protected boolean handleQuestionMark(Project project, Editor editor, PsiFile file, int offsetBefore) {
    return false;
  }

  protected boolean handleAnnotationParameter(Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return false;
  }

  @Override
  public @NotNull Result beforeCharTyped(final char c,
                                         final @NotNull Project project,
                                         final @NotNull Editor editor,
                                         final @NotNull PsiFile file,
                                         final @NotNull FileType fileType) {
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

      HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
      CharSequence sequence = editor.getDocument().getCharsSequence();
      if (!iterator.atEnd() && iterator.getStart() == offset &&
          (iterator.getTokenType() == JavaTokenType.STRING_TEMPLATE_END ||
           iterator.getTokenType() == JavaTokenType.TEXT_BLOCK_TEMPLATE_END ||
           iterator.getTokenType() == JavaTokenType.STRING_TEMPLATE_MID ||
           iterator.getTokenType() == JavaTokenType.TEXT_BLOCK_TEMPLATE_MID)) {
        if (sequence.length() > offset && sequence.charAt(offset) == '}') {
          editor.getCaretModel().moveToOffset(offset + 1);
          return Result.STOP;
        }
      }
    }
    if (fileType instanceof JavaFileType && c == '{') {
      final int offset = editor.getCaretModel().getOffset();
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
      if (PsiTreeUtil.getParentOfType(leaf, PsiArrayInitializerExpression.class, false, PsiCodeBlock.class, PsiMember.class) != null) {
        return Result.CONTINUE;
      }
      PsiElement st = leaf != null ? leaf.getParent() : null;
      PsiElement prev = offset > 1 ? file.findElementAt(offset - 1) : null;
      if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET && isRparenth(leaf) &&
          (st instanceof PsiWhileStatement || st instanceof PsiIfStatement) &&
          shouldInsertStatementBody(st, doc, prev)) {
        return processWhileAndIfStatementBody(project, editor, file);
      }

      if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET && afterArrowInCase(leaf) &&
          iterator.getTokenType() != JavaTokenType.LBRACKET) {
        Result stop = processOpenBraceInOneLineCaseRule(project, editor, file, leaf);
        if (stop != null) return stop;
      }

      if (PsiTreeUtil.getParentOfType(leaf, PsiCodeBlock.class, false, PsiMember.class) != null &&
          !shouldInsertPairedBrace(leaf)) {
        EditorModificationUtilEx.insertStringAtCaret(editor, "{");
        TypedHandler.indentOpenedBrace(project, editor);
        return Result.STOP; // use case: manually wrapping part of method's code in 'if', 'while', etc
      }
    }

    return Result.CONTINUE;
  }

  private static @Nullable Result processOpenBraceInOneLineCaseRule(@NotNull Project project,
                                                                    @NotNull Editor editor,
                                                                    @NotNull PsiFile file,
                                                                    @NotNull PsiElement leaf) {
    PsiElement rule = PsiTreeUtil.getParentOfType(leaf, PsiSwitchLabeledRuleStatement.class);
    if(rule != null) {
      while (true) {
        PsiElement next = rule.getNextSibling();
        if (next instanceof PsiWhiteSpace) {
          next = next.getNextSibling();
        }
        if(next instanceof PsiThrowStatement ||
           next instanceof PsiExpressionStatement) {
          rule = next;
          continue;
        }
        break;
      }
    }
    if (rule != null) {
      int firstOffset = editor.getCaretModel().getOffset();
      editor.getDocument().insertString(firstOffset, "{");
      boolean hasFirstBreakLine = true;
      int expectedIndex = firstOffset - leaf.getTextRange().getStartOffset();
      if (expectedIndex >= 0 && expectedIndex < leaf.getText().length()) {
        String text = leaf.getText().substring(expectedIndex);
        if (!text.contains("\n") &&
            !text.contains("\r")) {
          hasFirstBreakLine = false;
          editor.getDocument().insertString(firstOffset + 1, "\n");
        }
      }
      editor.getCaretModel().moveToOffset(firstOffset + 1);
      int secondOffset = rule.getTextRange().getEndOffset() + (hasFirstBreakLine ? 1 : 2);
      editor.getDocument().insertString(secondOffset, "\n}");
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      CodeStyleManager.getInstance(project).adjustLineIndent(file, secondOffset + 1);
      if (!hasFirstBreakLine) {
        CodeStyleManager.getInstance(project).adjustLineIndent(file, firstOffset + 2);
      }
      return Result.STOP;
    }
    return null;
  }

  private static boolean afterArrowInCase(@Nullable PsiElement leaf) {
    if (leaf == null) return false;
    IElementType leafElementType = leaf.getNode().getElementType();
    if (leafElementType == JavaTokenType.STRING_LITERAL ||
        leafElementType == JavaTokenType.TEXT_BLOCK_LITERAL ||
        leafElementType == JavaTokenType.CHARACTER_LITERAL) {
      return false;
    }
    PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf(leaf);
    if (prevLeaf == null) return false;
    if (prevLeaf.getNode().getElementType() != JavaTokenType.ARROW) return false;
    PsiElement parent = prevLeaf.getParent();
    if (parent == null) return false;
    if (!(parent instanceof PsiSwitchLabeledRuleStatement)) return false;
    if (StringUtil.isEmptyOrSpaces(leaf.getText())) {
      leaf = PsiTreeUtil.nextVisibleLeaf(leaf);
    }
    PsiElement body =
      leaf instanceof PsiExpressionStatement || leaf instanceof PsiThrowStatement ? leaf :
      PsiTreeUtil.getParentOfType(leaf, PsiExpressionStatement.class, PsiThrowStatement.class);
    if (body == null) return false;
    return PsiTreeUtil.isAncestor(parent, body, false);
  }

  private static boolean shouldInsertPairedBrace(@NotNull PsiElement leaf) {
    PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf(leaf);
    // lambda
    if (prevLeaf != null && prevLeaf.getNode().getElementType() == JavaTokenType.ARROW) return true;
    // anonymous class
    if (PsiTreeUtil.getParentOfType(prevLeaf, PsiNewExpression.class, true, PsiCodeBlock.class, PsiMember.class) != null) return true;
    // local class
    if (prevLeaf instanceof PsiIdentifier && prevLeaf.getParent() instanceof PsiClass) return true;
    // local record
    if (PsiUtil.isJavaToken(prevLeaf, JavaTokenType.RPARENTH) && prevLeaf.getParent() instanceof PsiRecordHeader) return true;
    return false;
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

  @Override
  public @NotNull Result charTyped(final char c, final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file) {
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
      if (parent instanceof PsiSwitchLabelStatement) {
        CodeStyleManager.getInstance(project).adjustLineIndent(file, parent.getTextOffset());
        return true;
      }
    }
    return false;
  }
}
