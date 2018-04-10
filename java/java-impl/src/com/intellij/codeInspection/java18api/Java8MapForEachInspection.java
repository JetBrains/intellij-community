// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java18api;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Objects;

/**
 * @author Tagir Valeev
 */
public class Java8MapForEachInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final String JAVA_UTIL_MAP_ENTRY = CommonClassNames.JAVA_UTIL_MAP + ".Entry";

  private static final CallMatcher ITERABLE_FOREACH =
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_ITERABLE, "forEach").parameterTypes(CommonClassNames.JAVA_UTIL_FUNCTION_CONSUMER);
  private static final CallMatcher MAP_ENTRY_SET =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "entrySet").parameterCount(0);
  private static final CallMatcher ENTRY_GETTER =
    CallMatcher.instanceCall(JAVA_UTIL_MAP_ENTRY, "getValue", "getKey").parameterCount(0);

  public boolean DO_NOT_HIGHLIGHT_LOOP = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionsBundle.message("inspection.map.foreach.option.no.loops"), this,
                                          "DO_NOT_HIGHLIGHT_LOOP");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (!ITERABLE_FOREACH.test(call)) return;
        PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
        if (!MAP_ENTRY_SET.test(qualifierCall)) return;
        PsiLambdaExpression lambda = ObjectUtils.tryCast(call.getArgumentList().getExpressions()[0], PsiLambdaExpression.class);
        if (lambda == null) return;
        PsiParameter[] lambdaParameters = lambda.getParameterList().getParameters();
        if (lambdaParameters.length != 1) return;
        PsiParameter entry = lambdaParameters[0];
        if (!allUsagesAllowed(entry)) return;
        PsiElement nameElement = Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement());
        holder.registerProblem(nameElement, InspectionsBundle.message("inspection.map.foreach.message"), new ReplaceWithMapForEachFix());
      }

      private boolean allUsagesAllowed(@NotNull PsiParameter entry) {
        return ReferencesSearch.search(entry).forEach(entryRef -> {
          PsiMethodCallExpression entryCall =
            ExpressionUtils.getCallForQualifier(ObjectUtils.tryCast(entryRef.getElement(), PsiExpression.class));
          return ENTRY_GETTER.test(entryCall);
        });
      }

      @Override
      public void visitForeachStatement(PsiForeachStatement loop) {
        if (DO_NOT_HIGHLIGHT_LOOP && !isOnTheFly) return;
        PsiMethodCallExpression call =
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(loop.getIteratedValue()), PsiMethodCallExpression.class);
        if (MAP_ENTRY_SET.test(call) &&
            LambdaGenerationUtil.canBeUncheckedLambda(loop.getBody()) &&
            allUsagesAllowed(loop.getIterationParameter())) {
          ProblemHighlightType type =
            DO_NOT_HIGHLIGHT_LOOP ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
          boolean wholeStatement =
            isOnTheFly && (DO_NOT_HIGHLIGHT_LOOP || InspectionProjectProfileManager.isInformationLevel(getShortName(), loop));
          TextRange range;
          PsiJavaToken rParenth = loop.getRParenth();
          PsiElement firstChild = loop.getFirstChild();
          PsiElement toHighlight;
          if (wholeStatement && rParenth != null) {
            toHighlight = loop;
            range = new TextRange(firstChild.getStartOffsetInParent(), rParenth.getStartOffsetInParent() + 1);
          }
          else {
            toHighlight = firstChild;
            range = new TextRange(0, firstChild.getTextLength());
          }
          holder.registerProblem(toHighlight, InspectionsBundle.message("inspection.map.foreach.message"),
                                 type, range, new ReplaceWithMapForEachFix());
        }
      }
    };
  }

  private static class ReplaceWithMapForEachFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.map.foreach.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      PsiElement foreach = element instanceof PsiForeachStatement ? element : element.getParent();
      if (foreach instanceof PsiForeachStatement) {
        fixInForeach((PsiForeachStatement)foreach);
        return;
      }
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiMethodCallExpression entrySetCall = MethodCallUtils.getQualifierMethodCall(call);
      if (entrySetCall == null) return;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 1) return;
      PsiLambdaExpression lambda = ObjectUtils.tryCast(args[0], PsiLambdaExpression.class);
      if (lambda == null) return;
      PsiElement body = lambda.getBody();
      if (body == null) return;
      PsiParameterList parameterList = lambda.getParameterList();
      PsiParameter[] lambdaParameters = parameterList.getParameters();
      if (lambdaParameters.length != 1) return;
      CommentTracker ct = new CommentTracker();
      PsiParameter entryParameter = lambdaParameters[0];
      String replacement = createReplacementExpression(entrySetCall, entryParameter, body, ct);
      ct.replaceAndRestoreComments(call, replacement);
    }

    private static String createReplacementExpression(PsiMethodCallExpression entrySetCall,
                                                      PsiParameter entryParameter,
                                                      PsiElement body,
                                                      CommentTracker ct) {
      PsiType entryType = entryParameter.getType();
      ParameterCandidate key = new ParameterCandidate(entryType, true);
      ParameterCandidate value = new ParameterCandidate(entryType, false);
      Collection<PsiReference> references = ReferencesSearch.search(entryParameter).findAll();
      for (PsiReference ref : references) {
        PsiMethodCallExpression entryCall = ExpressionUtils.getCallForQualifier(ObjectUtils.tryCast(ref.getElement(), PsiExpression.class));
        if (ENTRY_GETTER.test(entryCall)) {
          ParameterCandidate.select(entryCall, key, value).accept(entryCall);
        }
      }
      key.createName(body, ct);
      value.createName(body, ct);
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(entrySetCall.getProject());
      for (PsiReference ref : references) {
        PsiExpression expression = ObjectUtils.tryCast(ref.getElement(), PsiExpression.class);
        if (expression == null || !expression.isValid()) continue;
        PsiMethodCallExpression entryCall = ExpressionUtils.getCallForQualifier(expression);
        if (ENTRY_GETTER.test(entryCall)) {
          ct.replace(entryCall, factory.createIdentifier(ParameterCandidate.select(entryCall, key, value).myName));
        }
      }
      String lambdaBody;
      if (body instanceof PsiExpression || body instanceof PsiCodeBlock || body instanceof PsiBlockStatement) {
        lambdaBody = ct.text(body);
      }
      else {
        lambdaBody = "{" + ct.text(body) + "}";
      }
      PsiLambdaExpression newLambda =
        (PsiLambdaExpression)factory.createExpressionFromText("(" + key.myName + "," + value.myName + ")->" + lambdaBody, body);
      LambdaRefactoringUtil.simplifyToExpressionLambda(newLambda);
      entrySetCall.getArgumentList().add(newLambda);
      ExpressionUtils.bindCallTo(entrySetCall, "forEach");
      return ct.text(entrySetCall);
    }

    private static void fixInForeach(PsiForeachStatement loop) {
      PsiMethodCallExpression entrySetCall =
        ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(loop.getIteratedValue()), PsiMethodCallExpression.class);
      if (entrySetCall == null) return;
      PsiElement body = loop.getBody();
      if (body == null) return;
      PsiParameter entryParameter = loop.getIterationParameter();
      CommentTracker ct = new CommentTracker();
      String replacementExpression = createReplacementExpression(entrySetCall, entryParameter, body, ct);
      ct.replaceAndRestoreComments(loop, replacementExpression + ";");
    }

    private static class ParameterCandidate {
      PsiVariable myOriginalVar;
      final PsiType myType;
      String myName;

      ParameterCandidate(PsiType entryType, boolean isKey) {
        myName = isKey ? "key" : "value";
        myType = GenericsUtil
          .getVariableTypeByExpressionType(PsiUtil.substituteTypeParameter(entryType, JAVA_UTIL_MAP_ENTRY, isKey ? 0 : 1, true));
      }

      private void createName(PsiElement context, CommentTracker ct) {
        if (myOriginalVar != null) {
          myName = myOriginalVar.getName();
          ct.delete(myOriginalVar);
        }
        else {
          myName = JavaCodeStyleManager.getInstance(context.getProject()).suggestUniqueVariableName(myName, context, true);
        }
      }

      public void accept(PsiMethodCallExpression call) {
        if (myOriginalVar != null) return;
        PsiLocalVariable variable = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(call.getParent()), PsiLocalVariable.class);
        if (variable != null && EquivalenceChecker.getCanonicalPsiEquivalence().typesAreEquivalent(variable.getType(), myType)) {
          myOriginalVar = variable;
        }
      }

      static ParameterCandidate select(PsiMethodCallExpression entryCall, ParameterCandidate key, ParameterCandidate value) {
        String methodName = entryCall.getMethodExpression().getReferenceName();
        return "getKey".equals(methodName) ? key : value;
      }
    }
  }
}