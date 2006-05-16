/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 13, 2002
 * Time: 3:26:50 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

public class CastMethodParametersFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CastMethodParametersFix");

  private final PsiExpressionList myArgList;
  private final int myIndex;
  private final PsiType myToType;

  private CastMethodParametersFix(PsiExpressionList list, int i, PsiType toType) {
    myArgList = list;
    myIndex = i;
    myToType = toType instanceof PsiEllipsisType ? ((PsiEllipsisType) toType).toArrayType() : toType;
  }

  @NotNull
  public String getText() {
    if (myArgList.getExpressions().length == 1) {
      return QuickFixBundle.message("cast.single.parameter.text", HighlightUtil.formatType(myToType));
    }

    return QuickFixBundle.message("cast.parameter.text", myIndex + 1, HighlightUtil.formatType(myToType));
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.typecast.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return
        myToType != null
        && myToType.isValid()
        && myArgList != null
        && myArgList.getExpressions().length > myIndex
        && myArgList.getExpressions()[myIndex] != null
        && myArgList.getExpressions()[myIndex].isValid();
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    PsiExpression expression = myArgList.getExpressions()[myIndex];

    try {
      PsiTypeCastExpression typeCast = AddTypeCastFix.createCastExpression(expression, project, myToType);
      expression.replace(typeCast);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public static void registerCastActions(CandidateInfo[] candidates, PsiCall call, PsiJavaCodeReferenceElement methodRef, HighlightInfo highlightInfo) {
    if (candidates.length == 0) return;
    List<CandidateInfo> methodCandidates = new ArrayList<CandidateInfo>(Arrays.asList(candidates));
    PsiExpressionList list = call.getArgumentList();
    PsiExpression[] expressions = list.getExpressions();
    if (expressions.length == 0) return;
    // filter out not castable candidates
    nextMethod:
    for (int i = methodCandidates.size() - 1; i >= 0; i--) {
      CandidateInfo candidate = methodCandidates.get(i);
      PsiMethod method = (PsiMethod) candidate.getElement();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (expressions.length != parameters.length) {
        methodCandidates.remove(i);
        continue;
      }
      for (int j = 0; j < parameters.length; j++) {
        PsiParameter parameter = parameters[j];
        PsiExpression expression = expressions[j];
        // check if we can cast to this method
        PsiType exprType = expression.getType();
        PsiType parameterType = substitutor.substitute(parameter.getType());
        if (exprType == null
            || parameterType == null
            || !TypeConversionUtil.areTypesConvertible(exprType, parameterType)) {
          methodCandidates.remove(i);
          continue nextMethod;
        }
      }
    }

    if (methodCandidates.size() == 0) return;

    try {
      for (int i = 0; i < expressions.length; i++) {
        PsiExpression expression = expressions[i];
        PsiType exprType = expression.getType();
        Set<String> suggestedCasts = new THashSet<String>();
        // find to which type we can cast this param to get valid method call
        for (CandidateInfo candidate : methodCandidates) {
          PsiMethod method = (PsiMethod)candidate.getElement();
          PsiSubstitutor substitutor = candidate.getSubstitutor();
          PsiParameter[] parameters = method.getParameterList().getParameters();
          PsiType originalParameterType = parameters[i].getType();
          PsiType parameterType = substitutor.substitute(originalParameterType);
          if (parameterType instanceof PsiWildcardType) continue;
          if (suggestedCasts.contains(parameterType.getCanonicalText())) continue;
          // strict compare since even widening cast may help
          if (Comparing.equal(exprType, parameterType)) continue;
          PsiCall newCall = (PsiCall) call.copy();
          PsiTypeCastExpression castExpression = AddTypeCastFix.createCastExpression(expression, methodRef.getProject(), parameterType);
          newCall.getArgumentList().getExpressions()[i].replace(castExpression);
          JavaResolveResult resolveResult = newCall.resolveMethodGenerics();
          if (resolveResult.getElement() != null && resolveResult.isValidResult()) {
            suggestedCasts.add(parameterType.getCanonicalText());
            QuickFixAction.registerQuickFixAction(highlightInfo, new CastMethodParametersFix(list, i, parameterType));
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
