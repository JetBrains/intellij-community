package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class PermuteArgumentsFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.PermuteArgumentsFix");
  private final PsiCall myCall;
  private final PsiCall myPermutation;

  private PermuteArgumentsFix(@NotNull PsiCall call, @NotNull PsiCall permutation) {
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
    myCall.getArgumentList().replace(myPermutation.getArgumentList());
  }

  public static void registerFix(HighlightInfo info, PsiCall callExpression, final CandidateInfo[] candidates, final TextRange fixRange) {
    PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
    if (expressions.length < 2) return;
    List<PsiCall> permutations = new ArrayList<PsiCall>();

    for (CandidateInfo candidate : candidates) {
      if (candidate instanceof MethodCandidateInfo) {
        MethodCandidateInfo methodCandidate = (MethodCandidateInfo)candidate;
        PsiMethod method = methodCandidate.getElement();
        PsiSubstitutor substitutor = methodCandidate.getSubstitutor();

        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (expressions.length != parameters.length || parameters.length ==0) continue;
        int minIncompatibleIndex = parameters.length;
        int maxIncompatibleIndex = 0;
        int incompatibilitiesCount = 0;
        for (int i = 0; i < parameters.length; i++) {
          PsiParameter parameter = parameters[i];
          PsiType type = substitutor.substitute(parameter.getType());
          if (TypeConversionUtil.areTypesAssignmentCompatible(type, expressions[i])) continue;
          if (minIncompatibleIndex == parameters.length) minIncompatibleIndex = i;
          maxIncompatibleIndex = i;
          incompatibilitiesCount++;
        }

        try {
          registerSwapFixes(expressions, callExpression, permutations, methodCandidate, incompatibilitiesCount, minIncompatibleIndex, maxIncompatibleIndex);
          registerShiftFixes(expressions, callExpression, permutations, methodCandidate, minIncompatibleIndex, maxIncompatibleIndex);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
    if (permutations.size() == 1) {
      PermuteArgumentsFix fix = new PermuteArgumentsFix(callExpression, permutations.get(0));
      QuickFixAction.registerQuickFixAction(info, fixRange, fix, null,null);
    }
  }

  private static void registerShiftFixes(final PsiExpression[] expressions, final PsiCall callExpression, final List<PsiCall> permutations,
                                         final MethodCandidateInfo methodCandidate, final int minIncompatibleIndex, final int maxIncompatibleIndex)
    throws IncorrectOperationException {
    PsiMethod method = methodCandidate.getElement();
    PsiSubstitutor substitutor = methodCandidate.getSubstitutor();
    // shift range should include both incompatible indexes
    for (int i = 0; i <= minIncompatibleIndex; i++) {
      for (int j = Math.max(i+2,maxIncompatibleIndex); j < expressions.length; j++) { // if j=i+1 the shift is equal to swap
        {
          ArrayUtil.rotateLeft(expressions, i, j);
          if (PsiUtil.isApplicable(method, substitutor, expressions)) {
            PsiCall copy = (PsiCall)callExpression.copy();
            PsiExpression[] copyExpressions = copy.getArgumentList().getExpressions();
            for (int k = i; k < copyExpressions.length; k++) {
              copyExpressions[k].replace(expressions[k]);
            }

            JavaResolveResult result = copy.resolveMethodGenerics();
            if (result.getElement() != null && result.isValidResult()) {
              permutations.add(copy);
              if (permutations.size() > 1) return;
            }
          }
          ArrayUtil.rotateRight(expressions, i, j);
        }

        {
          ArrayUtil.rotateRight(expressions, i, j);
          if (PsiUtil.isApplicable(method, substitutor, expressions)) {
            PsiCall copy = (PsiCall)callExpression.copy();
            PsiExpression[] copyExpressions = copy.getArgumentList().getExpressions();
            for (int k = i; k < copyExpressions.length; k++) {
              copyExpressions[k].replace(expressions[k]);
            }

            JavaResolveResult result = copy.resolveMethodGenerics();
            if (result.getElement() != null && result.isValidResult()) {
              permutations.add(copy);
              if (permutations.size() > 1) return;
            }
          }
          ArrayUtil.rotateLeft(expressions, i, j);
        }
      }
    }
  }

  private static void registerSwapFixes(final PsiExpression[] expressions, final PsiCall callExpression, final List<PsiCall> permutations,
                                        MethodCandidateInfo candidate, final int incompatibilitiesCount, final int minIncompatibleIndex,
                                        final int maxIncompatibleIndex) throws IncorrectOperationException {
      PsiMethod method = candidate.getElement();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
    if (incompatibilitiesCount >= 3) return; // no way we can fix it by swapping

    for (int i = minIncompatibleIndex; i < maxIncompatibleIndex; i++) {
      for (int j = i+1; j <= maxIncompatibleIndex; j++) {
        ArrayUtil.swap(expressions, i, j);
        if (PsiUtil.isApplicable(method, substitutor, expressions)) {
          PsiCall copy = (PsiCall)callExpression.copy();
          PsiExpression[] copyExpressions = copy.getArgumentList().getExpressions();
          copyExpressions[i].replace(expressions[i]);
          copyExpressions[j].replace(expressions[j]);
          JavaResolveResult result = copy.resolveMethodGenerics();
          if (result.getElement() != null && result.isValidResult()) {
            permutations.add(copy);
            if (permutations.size() > 1) return;
          }
        }
        ArrayUtil.swap(expressions, i, j);
      }
    }
  }
}
