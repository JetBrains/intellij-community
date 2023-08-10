// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaClassReferenceCompletionContributor;
import com.intellij.codeInsight.editorActions.smartEnter.JavaSmartEnterProcessor;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
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
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaTypedHandler extends TypedHandlerDelegate {
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
      if (parent instanceof PsiParameterList ||
          (parent instanceof PsiParameter && !(parent instanceof PsiPatternVariable))) {
        return false;
      }

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

  @Override
  public @NotNull Result checkAutoPopup(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return Result.CONTINUE;

    int offset = editor.getCaretModel().getOffset();
    if (charTyped == ' ' &&
        StringUtil.endsWith(editor.getDocument().getImmutableCharSequence(), 0, offset, PsiKeyword.NEW)) {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor, CompletionType.BASIC, f -> {
        PsiElement leaf = f.findElementAt(offset - PsiKeyword.NEW.length());
        return leaf instanceof PsiKeyword &&
               leaf.textMatches(PsiKeyword.NEW) &&
               !PsiJavaPatterns.psiElement().insideStarting(PsiJavaPatterns.psiExpressionStatement()).accepts(leaf);
      });
      return Result.STOP;
    }

    return super.checkAutoPopup(charTyped, project, editor, file);
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
        if (TypedHandlerUtil.handleGenericGT(editor, JavaTokenType.LT, JavaTokenType.GT, JavaTypingTokenSets.INVALID_INSIDE_REFERENCE)) return Result.STOP;
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
    if (fileType == JavaFileType.INSTANCE && c == '{') {
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
      PsiDocumentManager.getInstance(project).commitDocument(doc);
      final PsiElement leaf = file.findElementAt(offset);
      if (PsiTreeUtil.getParentOfType(leaf, PsiArrayInitializerExpression.class, false, PsiCodeBlock.class, PsiMember.class) != null) {
        return Result.CONTINUE;
      }
      PsiElement st = leaf != null ? leaf.getParent() : null;
      PsiElement prev = offset > 1 ? file.findElementAt(offset - 1) : null;
      if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET && isRparenth(leaf) &&
          (st instanceof PsiWhileStatement || st instanceof PsiIfStatement) && shouldInsertStatementBody(st, doc, prev)) {
        CommandProcessor.getInstance().executeCommand(project, () -> new JavaSmartEnterProcessor().process(project, editor, file),
                                                      JavaBundle.message("command.name.insert.block.statement"), null);
        return Result.STOP;
      }

      if (PsiTreeUtil.getParentOfType(leaf, PsiCodeBlock.class, false, PsiMember.class) != null && !shouldInsertPairedBrace(leaf)) {
        EditorModificationUtilEx.insertStringAtCaret(editor, "{");
        TypedHandler.indentOpenedBrace(project, editor);
        return Result.STOP; // use case: manually wrapping part of method's code in 'if', 'while', etc
      }
    }

    return Result.CONTINUE;
  }

  /**
   * Automatically inserts parentheses if != or == was typed after a&b, a|b or a^b where a and b are numbers.
   *
   * @return true if the '=' char was processed
   */
  private static boolean handleEquality(Project project, Editor editor, PsiFile file, int offsetBefore) {
    if (offsetBefore == 0) return false;
    Document doc = editor.getDocument();
    char prevChar = doc.getCharsSequence().charAt(offsetBefore - 1);
    if (prevChar != '=' && prevChar != '!') return false;

    HighlighterIterator it = editor.getHighlighter().createIterator(offsetBefore - 1);
    IElementType curToken = it.getTokenType();
    if (curToken != JavaTokenType.EQ && curToken != JavaTokenType.EXCL) return false;
    int lineStart = doc.getLineStartOffset(doc.getLineNumber(offsetBefore));
    do {
      it.retreat();
      if (it.atEnd()) return false;
      curToken = it.getTokenType();
    }
    while (curToken == TokenType.WHITE_SPACE || curToken == JavaTokenType.C_STYLE_COMMENT || curToken == JavaTokenType.END_OF_LINE_COMMENT);
    // ) == or ) != : definitely no need to add parentheses
    if (curToken == JavaTokenType.RPARENTH) return false;
    while (true) {
      if (it.getStart() < lineStart) return false;
      it.retreat();
      if (it.atEnd()) return false;
      curToken = it.getTokenType();
      if (curToken == JavaTokenType.AND || curToken == JavaTokenType.OR || curToken == JavaTokenType.XOR) break;
    }

    doc.insertString(offsetBefore, "=");
    editor.getCaretModel().moveToOffset(offsetBefore + 1);
    // a&b== => (a&b)==
    PsiDocumentManager.getInstance(project).commitDocument(doc);
    PsiJavaToken token = ObjectUtils.tryCast(file.findElementAt(offsetBefore), PsiJavaToken.class);
    if (token == null) return true;
    IElementType type = token.getTokenType();
    if (type != JavaTokenType.EQEQ && type != JavaTokenType.NE) return true;
    PsiBinaryExpression comparison = ObjectUtils.tryCast(token.getParent(), PsiBinaryExpression.class);
    if (comparison == null || comparison.getROperand() != null) return true;
    PsiBinaryExpression bitwiseOp = ObjectUtils.tryCast(comparison.getParent(), PsiBinaryExpression.class);
    if (bitwiseOp == null || bitwiseOp.getROperand() != comparison) return true;
    IElementType bitwiseOpType = bitwiseOp.getOperationTokenType();
    if (bitwiseOpType != JavaTokenType.AND && bitwiseOpType != JavaTokenType.OR && bitwiseOpType != JavaTokenType.XOR) return true;
    PsiExpression left = bitwiseOp.getLOperand();
    PsiExpression right = comparison.getLOperand();
    if (!TypeConversionUtil.isIntegralNumberType(left.getType()) || !TypeConversionUtil.isIntegralNumberType(right.getType())) {
      return true;
    }
    int openingOffset = left.getTextRange().getStartOffset();
    int closingOffset = right.getTextRange().getEndOffset();
    wrapWithParentheses(file, doc, openingOffset, closingOffset);
    return true;
  }

  /**
   * Automatically insert parentheses around the ?: when necessary.
   *
   * @return true if question mark was handled
   */
  private static boolean handleQuestionMark(Project project, Editor editor, PsiFile file, int offsetBefore) {
    if (offsetBefore == 0) return false;
    HighlighterIterator it = editor.getHighlighter().createIterator(offsetBefore);
    if (it.atEnd()) return false;
    IElementType curToken = it.getTokenType();
    if (JavaTypingTokenSets.UNWANTED_TOKEN_AT_QUESTION.contains(curToken)) return false;
    int nesting = 0;
    while (true) {
      it.retreat();
      if (it.atEnd()) return false;
      curToken = it.getTokenType();
      if (curToken == JavaTokenType.LPARENTH || curToken == JavaTokenType.LBRACKET || curToken == JavaTokenType.LBRACE) {
        nesting--;
        if (nesting < 0) return false;
      }
      else if (curToken == JavaTokenType.RPARENTH || curToken == JavaTokenType.RBRACKET || curToken == JavaTokenType.RBRACE) {
        nesting++;
      }
      else if (nesting == 0) {
        if (JavaTypingTokenSets.UNWANTED_TOKEN_BEFORE_QUESTION.contains(curToken)) return false;
        if (JavaTypingTokenSets.WANTED_TOKEN_BEFORE_QUESTION.contains(curToken)) break;
      }
    }

    Document doc = editor.getDocument();
    doc.insertString(offsetBefore, "?");
    editor.getCaretModel().moveToOffset(offsetBefore + 1);
    PsiDocumentManager.getInstance(project).commitDocument(doc);
    PsiElement element = file.findElementAt(offsetBefore);
    if (!PsiUtil.isJavaToken(element, JavaTokenType.QUEST)) return true;
    PsiConditionalExpression cond = ObjectUtils.tryCast(element.getParent(), PsiConditionalExpression.class);
    if (cond == null || cond.getThenExpression() != null || cond.getElseExpression() != null) return true;
    PsiExpression condition = cond.getCondition();
    if (PsiUtilCore.hasErrorElementChild(condition)) return true;
    // intVal+bool? => intVal+(bool?)
    if (condition instanceof PsiPolyadicExpression && !PsiTypes.booleanType().equals(condition.getType())) {
      PsiExpression lastOperand = ArrayUtil.getLastElement(((PsiPolyadicExpression)condition).getOperands());
      if (lastOperand != null && PsiTypes.booleanType().equals(lastOperand.getType())) {
        int openingOffset = lastOperand.getTextRange().getStartOffset();
        int closingOffset = cond.getTextRange().getEndOffset();
        wrapWithParentheses(file, doc, openingOffset, closingOffset);
      }
    }
    return true;
  }

  private static void wrapWithParentheses(PsiFile file, Document doc, int openingOffset, int closingOffset) {
    String space = CodeStyle.getLanguageSettings(file).SPACE_WITHIN_PARENTHESES ? " " : "";
    doc.insertString(closingOffset, space + ")");
    doc.insertString(openingOffset, "(" + space);
  }

  private static boolean shouldInsertPairedBrace(@NotNull PsiElement leaf) {
    PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf(leaf);
    // lambda
    if (PsiUtil.isJavaToken(prevLeaf, JavaTokenType.ARROW)) return true;
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

  @NotNull
  @Override
  public Result charTyped(final char c, @NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return Result.CONTINUE;

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
    HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
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

  private static boolean handleSemicolon(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
    if (fileType != JavaFileType.INSTANCE) return false;
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
}
