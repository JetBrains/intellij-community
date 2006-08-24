package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

/**
 * @author cdr
 */
public class PermuteArgumentsFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.PermuteArgumentsFix");
  private final PsiCall myCall;
  private final int[] myPermutation;

  private PermuteArgumentsFix(@NotNull PsiCall call, @NotNull int[] permutation) {
    myCall = call;
    myPermutation = permutation;
  }

  public boolean startInWriteAction() {
    return true;
  }


  @NotNull
  public String getText() {
    return "Permute arguments";
  }


  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return !project.isDisposed() && myCall.isValid() && myCall.getManager().isInProject(myCall);
  }                               

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiCall copy = (PsiCall)myCall.copy();
    PsiExpression[] expressions = myCall.getArgumentList().getExpressions();
    PsiExpression[] copyExpressions = copy.getArgumentList().getExpressions();
    for (int i = 0; i < myPermutation.length; i++) {
      int j = myPermutation[i];
      if (i != j) {
        copyExpressions[j].replace(expressions[i]);
      }
    }
    myCall.getArgumentList().replace(copy.getArgumentList());
  }

  public static void registerFix(HighlightInfo info, PsiCall callExpression, final TextRange fixRange) {
    PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
    if (expressions.length < 2) return;
    PsiType[] types = new PsiType[expressions.length];
    for (int i = 0; i < expressions.length; i++) {
      PsiExpression expression = expressions[i];
      types[i] = expression.getType();
    }

    List<int[]> permutations = new ArrayList<int[]>();
    try {
      registerSwapFixes(expressions, callExpression, permutations);
      registerShiftFixes(expressions, callExpression, permutations);
      if (permutations.size() == 1) {
        PermuteArgumentsFix fix = new PermuteArgumentsFix(callExpression, permutations.get(0));
        QuickFixAction.registerQuickFixAction(info, fixRange, fix, null,null);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void registerShiftFixes(final PsiExpression[] expressions, final PsiCall callExpression, final List<int[]> permutations)
    throws IncorrectOperationException {
    for (int i = 0; i < expressions.length; i++) {
      for (int j = i+2; j < expressions.length; j++) { // if j=i+1 the shift is equal to swap
        {
          PsiCall copy = (PsiCall)callExpression.copy();
          PsiExpression[] copyExpressions = copy.getArgumentList().getExpressions();
          for (int k = i; k < j; k++) {
            copyExpressions[k].replace(expressions[k+1]);
          }
          copyExpressions[j].replace(expressions[i]);

          JavaResolveResult result = copy.resolveMethodGenerics();
          if (result.getElement() != null && result.isValidResult()) {
            int[] permutation = new int[expressions.length];
            for (int k = 0; k < permutation.length; k++) {
              permutation[k] = k;
            }
            for (int k = i; k < j; k++) {
              permutation[k+1] = k;
            }
            permutation[i] = j;
            permutations.add(permutation);
            if (permutations.size() > 1) return;
          }
        }
        {
          PsiCall copy = (PsiCall)callExpression.copy();
          PsiExpression[] copyExpressions = copy.getArgumentList().getExpressions();
          for (int k = i; k < j; k++) {
            copyExpressions[k+1].replace(expressions[k]);
          }
          copyExpressions[i].replace(expressions[j]);

          JavaResolveResult result = copy.resolveMethodGenerics();
          if (result.getElement() != null && result.isValidResult()) {
            int[] permutation = new int[expressions.length];
            for (int k = 0; k < permutation.length; k++) {
              permutation[k] = k;
            }
            for (int k = i; k < j; k++) {
              permutation[k] = k+1;
            }
            permutation[j] = i;
            permutations.add(permutation);
            if (permutations.size() > 1) return;
          }
        }
      }
    }
  }

  private static void registerSwapFixes(final PsiExpression[] expressions, final PsiCall callExpression, final List<int[]> permutations) throws IncorrectOperationException {
    for (int i = 0; i < expressions.length; i++) {
      for (int j = i+1; j < expressions.length; j++) {
        PsiCall copy = (PsiCall)callExpression.copy();
        PsiExpression[] copyExpressions = copy.getArgumentList().getExpressions();
        copyExpressions[i].replace(expressions[j]);
        copyExpressions[j].replace(expressions[i]);
        JavaResolveResult result = copy.resolveMethodGenerics();
        if (result.getElement() != null && result.isValidResult()) {
          int[] permutation = new int[expressions.length];
          for (int k = 0; k < expressions.length; k++) {
            permutation[k] = k;
          }
          permutation[i] = j;
          permutation[j] = i;
          permutations.add(permutation);
          if (permutations.size() > 1) return;
        }
      }
    }
  }
}
