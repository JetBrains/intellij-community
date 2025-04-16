// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaClassReferenceCompletionContributor;
import com.intellij.codeInsight.completion.command.CommandCompletionFactoryKt;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JavaTypedHandler extends JavaTypedHandlerBase {
  @Override
  public boolean handleEquality(Project project, Editor editor, PsiFile file, int offsetBefore) {
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

  private static void wrapWithParentheses(PsiFile file, Document doc, int openingOffset, int closingOffset) {
    String space = CodeStyle.getLanguageSettings(file).SPACE_WITHIN_PARENTHESES ? " " : "";
    doc.insertString(closingOffset, space + ")");
    doc.insertString(openingOffset, "(" + space);
  }

  @Override
  protected boolean handleQuestionMark(Project project, Editor editor, PsiFile file, int offsetBefore) {
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

  @Override
  protected boolean handleAnnotationParameter(Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    int caret = editor.getCaretModel().getOffset();
    if (mightBeInsideDefaultAnnotationAttribute(editor, caret - 2)) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      PsiAnnotation anno =
        PsiTreeUtil.getParentOfType(file.findElementAt(caret), PsiAnnotation.class, false, PsiExpression.class, PsiComment.class);
      PsiNameValuePair attr = anno == null ? null : getTheOnlyDefaultAttribute(anno);
      if (attr != null && hasDefaultArrayMethod(anno) && !(attr.getValue() instanceof PsiArrayInitializerMemberValue)) {
        editor.getDocument().insertString(caret, "}");
        editor.getDocument().insertString(attr.getTextRange().getStartOffset(), "{");
        return true;
      }
    }
    return false;
  }

  private static @Nullable PsiNameValuePair getTheOnlyDefaultAttribute(@NotNull PsiAnnotation anno) {
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

  @Override
  protected void autoPopupMemberLookup(@NotNull Project project, final @NotNull Editor editor) {
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
      }
      while (parent instanceof PsiJavaCodeReferenceElement || parent instanceof PsiTypeElement);
      if (parent instanceof PsiParameterList list && PsiTreeUtil.isAncestor(list, lastElement, false) ||
          (parent instanceof PsiParameter && !(parent instanceof PsiPatternVariable))) {
        if (CommandCompletionFactoryKt.commandCompletionEnabled() &&
            parent instanceof PsiParameter parameter &&
            lastElement instanceof PsiJavaToken javaToken &&
            javaToken.textMatches(".") &&
            prevSibling instanceof PsiIdentifier identifier &&
            parameter.getIdentifyingElement() == identifier) {
          return true;
        }
        return false;
      }

      if (!".".equals(lastElement.getText()) && !"#".equals(lastElement.getText())) {
        return JavaClassReferenceCompletionContributor.findJavaClassReference(file, offset - 1) != null;
      }
      else {
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
        StringUtil.endsWith(editor.getDocument().getImmutableCharSequence(), 0, offset, JavaKeywords.NEW)) {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor, CompletionType.BASIC, f -> {
        PsiElement leaf = f.findElementAt(offset - JavaKeywords.NEW.length());
        return leaf instanceof PsiKeyword &&
               leaf.textMatches(JavaKeywords.NEW) &&
               !PsiJavaPatterns.psiElement().insideStarting(PsiJavaPatterns.psiExpressionStatement()).accepts(leaf);
      });
      return Result.STOP;
    }

    return super.checkAutoPopup(charTyped, project, editor, file);
  }

  @Override
  protected void autoPopupJavadocLookup(final @NotNull Project project, final @NotNull Editor editor) {
    AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, file -> {
      int offset = editor.getCaretModel().getOffset();

      PsiElement lastElement = file.findElementAt(offset - 1);
      return lastElement != null && StringUtil.endsWithChar(lastElement.getText(), '@');
    });
  }
}
