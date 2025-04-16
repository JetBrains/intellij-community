// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public class MethodCallFixer implements Fixer {
  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(astNode);
    PsiExpressionList argList = null;
    if (psiElement instanceof PsiMethodCallExpression && psiElement.getLanguage().equals(JavaLanguage.INSTANCE)) {
      argList = ((PsiMethodCallExpression)psiElement).getArgumentList();
    }
    else if (psiElement instanceof PsiNewExpression) {
      argList = ((PsiNewExpression)psiElement).getArgumentList();
    }

    int caret = editor.getCaretModel().getOffset();
    if (argList != null && !hasRParenth(argList)) {
      PsiCallExpression innermostCall =
        PsiTreeUtil.findElementOfClassAtOffset(psiElement.getContainingFile(), caret - 1, PsiCallExpression.class, false);

      while (innermostCall != null && innermostCall != psiElement && hasRParenth(innermostCall.getArgumentList())
             && innermostCall.resolveMethodGenerics().isValidResult()) {
        innermostCall = PsiTreeUtil.getParentOfType(innermostCall, PsiCallExpression.class);
      }
      if (innermostCall == null) return;

      argList = innermostCall.getArgumentList();
      if (argList == null) return;

      int endOffset = getMissingParenthesisOffset(argList);

      TextRange argListRange = argList.getTextRange();
      if (endOffset == -1) {
        endOffset = argListRange.getEndOffset();
      }

      PsiExpression[] args = argList.getExpressions();
      if (args.length > 0 &&
          startLine(editor, argList) != startLine(editor, args[0]) &&
          caret < args[0].getTextRange().getStartOffset()) {
        endOffset = argListRange.getStartOffset() + 1;
      }

      if (args.length > 0 && !DumbService.isDumb(argList.getProject())) {
        int caretArg = getCaretArgIndex(caret, argListRange, args);

        LongRangeSet innerCounts = getPossibleParameterCounts(innermostCall).meet(LongRangeSet.range(caretArg, args.length));
        if (!innerCounts.isEmpty()) {
          innerCounts = tryFilterByOuterCall(innermostCall, args, innerCounts);
          int minArg = (int)innerCounts.min();
          if (minArg > 0) {
            endOffset = Math.min(endOffset, args[minArg - 1].getTextRange().getEndOffset());
          }
        }
      }

      endOffset = CharArrayUtil.shiftBackward(editor.getDocument().getCharsSequence(), endOffset - 1, " \t\n") + 1;
      editor.getDocument().insertString(endOffset, ")");
    }
  }

  private static int getCaretArgIndex(int caret, TextRange argListRange, PsiExpression[] args) {
    int caretArg = 0;
    while (caretArg < args.length) {
      if (args[caretArg].getStartOffsetInParent() + argListRange.getStartOffset() > caret) {
        break;
      }
      caretArg++;
    }
    return caretArg;
  }

  private static int getMissingParenthesisOffset(PsiExpressionList argList) {
    PsiElement child = argList.getFirstChild();
    while (child != null) {
      if (child instanceof PsiErrorElement errorElement && errorElement.getErrorDescription().contains("')'")) {
        return errorElement.getTextRange().getStartOffset();
      }
      child = child.getNextSibling();
    }
    return -1;
  }

  private static boolean hasRParenth(PsiExpressionList args) {
    if (args == null) return false;
    PsiElement parenth = args.getLastChild();
    return parenth != null && ")".equals(parenth.getText());
  }

  private static @NotNull LongRangeSet tryFilterByOuterCall(@NotNull PsiCallExpression innermostCall,
                                                            @NotNull PsiExpression @NotNull [] args,
                                                            @NotNull LongRangeSet innerCounts) {
    PsiExpressionList outerArgList = ObjectUtils.tryCast(innermostCall.getParent(), PsiExpressionList.class);
    if (outerArgList != null) {
      PsiExpression[] outerArgs = outerArgList.getExpressions();
      if (innermostCall == ArrayUtil.getLastElement(outerArgs)) {
        PsiCallExpression outerCall = ObjectUtils.tryCast(outerArgList.getParent(), PsiCallExpression.class);
        if (outerCall != null) {
          LongRangeSet outerCounts = getPossibleParameterCounts(outerCall);
          if (!outerCounts.isEmpty()) {
            LongRangeSet allowedByOuter =
              LongRangeSet.point(args.length).minus(
                outerCounts.minus(LongRangeSet.point(outerArgs.length), LongRangeType.INT32), LongRangeType.INT32);
            LongRangeSet innerCountsFiltered = innerCounts.meet(allowedByOuter);
            if (!innerCountsFiltered.isEmpty()) {
              return innerCountsFiltered;
            }
          }
        }
      }
    }
    return innerCounts;
  }

  private static @NotNull LongRangeSet getPossibleParameterCounts(@NotNull PsiCallExpression call) {
    LongRangeSet counts = LongRangeSet.empty();
    for (CandidateInfo candidate : PsiResolveHelper.getInstance(call.getProject()).getReferencedMethodCandidates(call, false)) {
      PsiMethod element = ObjectUtils.tryCast(candidate.getElement(), PsiMethod.class);
      if (element != null) {
        int count = element.getParameterList().getParametersCount();
        counts = counts.join(element.isVarArgs() ? LongRangeSet.range(count - 1, Integer.MAX_VALUE) : LongRangeSet.point(count));
      }
    }
    return counts;
  }

  private static int startLine(Editor editor, PsiElement psiElement) {
    return editor.getDocument().getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
