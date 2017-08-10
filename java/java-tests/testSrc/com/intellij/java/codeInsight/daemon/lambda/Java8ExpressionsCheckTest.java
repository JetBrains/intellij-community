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
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

public class Java8ExpressionsCheckTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/expressions";

  public void testSecondConflictResolutionOnSameMethodCall() {
    doTestAllMethodCallExpressions();
  }

  public void testNestedLambdaAdditionalConstraints() {
    doTestAllMethodCallExpressions();
  }

  public void testAvoidClassRefCachingDuringInference() {
    doTestAllMethodCallExpressions();
  }

  public void testInfinitiveParameterBoundsCheck() {
    doTestAllMethodCallExpressions();
  }

  public void testProoveThatInferenceInsideLambdaBodyDontInfluenceOuterCallInference() {
    doTestAllMethodCallExpressions();
  }

  public void testDontCollectUnhandledReferencesInsideLambdaBody() {
    doTestAllMethodCallExpressions();
  }

  public void testCachedUnresolvedMethods() {
    doTestCachedUnresolved();
  }

  public void testCacheUnresolvedMethods2() {
    doTestCachedUnresolved();
  }
  
  public void testCacheUnresolvedMethods3() {
    doTestCachedUnresolved();
  }

  public void testCacheUnresolvedMethods4() {
    doTestCachedUnresolved();
  }

  public void testCacheUnresolvedMethods5() {
    doTestCachedUnresolved();
  }

  public void testMethodOverloadsInsideLambdaHierarchy() {
    doTestAllMethodCallExpressions();
  }

  public void testObjectOverloadsWithDiamondsOverMultipleConstructors() {
    doTestAllMethodCallExpressions();
  }

  public void testLambdaParameterTypeSideEffects() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    Collection<PsiParameter> parameters = PsiTreeUtil.findChildrenOfType(getFile(), PsiParameter.class);
    for (PsiParameter parameter : parameters) {
      if (parameter.getTypeElement() == null) { //lambda parameter
        assertNotNull(parameter.getType());
        Collection<PsiCallExpression> expressions = PsiTreeUtil.findChildrenOfType(getFile(), PsiCallExpression.class);
        for (PsiCallExpression expression : expressions) {
          assertNotNull(expression.getText(), expression.resolveMethod());
        }

        getPsiManager().dropResolveCaches();
      }
    }
  }

  public void testCachingOfResultsDuringCandidatesIteration() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    final Collection<PsiMethodCallExpression> methodCallExpressions = PsiTreeUtil.findChildrenOfType(getFile(), PsiMethodCallExpression.class);

    final PsiResolveHelper helper = JavaPsiFacade.getInstance(getProject()).getResolveHelper();
    for (PsiMethodCallExpression expression : methodCallExpressions) {
      CandidateInfo[] candidates = helper.getReferencedMethodCandidates(expression, false, true);
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] args = argumentList.getExpressions();
      for (JavaResolveResult result : candidates) {
        if (result instanceof MethodCandidateInfo) {
          final MethodCandidateInfo info = (MethodCandidateInfo)result;
          MethodCandidateInfo.ourOverloadGuard
            .doPreventingRecursion(argumentList, false, () -> info.inferTypeArguments(DefaultParameterTypeInferencePolicy.INSTANCE, args, true));
        }
      }

      PsiMethodCallExpression parentCall = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class, true);
      if (parentCall != null) {
        JavaResolveResult result = parentCall.getMethodExpression().advancedResolve(false);
        if (result instanceof MethodCandidateInfo) {
          assertNull(((MethodCandidateInfo)result).getInferenceErrorMessage());
        }
      }
    }
  }

  public void testNonCachingFolding() {
    final String filePath = BASE_PATH + "/" + getTestName(false) + ".java";
    configureByFile(filePath);
    PsiNewExpression newWithAnonym =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiNewExpression.class);
    ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes(newWithAnonym, false);
    assertNotNull(types);

    doTestConfiguredFile(false, false, filePath);
  }

  public void testRejectCachedTopLevelSessionIfItCorrespondsToTheWrongOverload() {
    final String filePath = BASE_PATH + "/" + getTestName(false) + ".java";
    configureByFile(filePath);
    PsiMethodCallExpression methodCall =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);
    assertNotNull(methodCall);
    final PsiResolveHelper helper = JavaPsiFacade.getInstance(methodCall.getProject()).getResolveHelper();
    CandidateInfo[] candidates = helper.getReferencedMethodCandidates(methodCall, false, true);
    for (CandidateInfo candidate : candidates) {
      if (candidate instanceof MethodCandidateInfo) {
        //try to cache top level session
        candidate.getSubstitutor();
      }
    }

    doTestConfiguredFile(false, false, filePath);
  }

  public void testCheckedExceptionConstraintToTopLevel() {
    doTestCachedUnresolved();
  }

  private void doTestCachedUnresolved() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    PsiMethodCallExpression callExpression =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);

    assertNotNull(callExpression);
    assertNotNull(callExpression.getType());

    final Collection<PsiCallExpression> methodCallExpressions = PsiTreeUtil.findChildrenOfType(getFile(), PsiCallExpression.class);
    for (PsiCallExpression expression : methodCallExpressions) {
      assertNotNull("Failed inference for: " + expression.getText(), expression.getType());
    }
  }

  public void testIDEA140035() {
    doTestAllMethodCallExpressions();
    final Collection<PsiParameter> parameterLists = PsiTreeUtil.findChildrenOfType(getFile(), PsiParameter.class);
    for (PsiParameter parameter : parameterLists) {
      if (parameter.getTypeElement() != null) continue;
      getPsiManager().dropResolveCaches();
      final PsiType type = parameter.getType();
      assertFalse("Failed inference for: " + parameter.getParent().getText(), type instanceof PsiLambdaParameterType);
    }
  }

  public void testAdditionalConstraintsBasedOnLambdaResolution() {
    doTestAllMethodCallExpressions();
  }
  
  public void testAdditionalConstraintsBasedOnLambdaResolutionForNestedLambdas() {
    doTestAllMethodCallExpressions();
  }

  public void testOverloadResolutionInsideLambdaInsideNestedCall() {
    doTestAllMethodCallExpressions();
  }

  private void doTestAllMethodCallExpressions() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    final Collection<PsiCallExpression> methodCallExpressions = PsiTreeUtil.findChildrenOfType(getFile(), PsiCallExpression.class);
    for (PsiCallExpression expression : methodCallExpressions) {
      getPsiManager().dropResolveCaches();
      if (expression instanceof PsiMethodCallExpression) {
        assertNotNull("Failed to resolve: " + expression.getText(), expression.resolveMethod());
      }
      assertNotNull("Failed inference for: " + expression.getText(), expression.getType());
    }

    final Collection<PsiNewExpression> parameterLists = PsiTreeUtil.findChildrenOfType(getFile(), PsiNewExpression.class);
    for (PsiNewExpression newExpression : parameterLists) {
      getPsiManager().dropResolveCaches();
      final PsiType[] arguments = newExpression.getTypeArguments();
      String failMessage = "Failed inference for: " + newExpression.getParent().getText();
      assertNotNull(failMessage, arguments);
      PsiDiamondType diamondType = PsiDiamondType.getDiamondType(newExpression);
      if (diamondType != null) {
        JavaResolveResult staticFactory = diamondType.getStaticFactory();
        assertNotNull(staticFactory);
        assertTrue(staticFactory instanceof MethodCandidateInfo);
        assertNull(failMessage, ((MethodCandidateInfo)staticFactory).getInferenceErrorMessage());
      }
    }
  }
}
