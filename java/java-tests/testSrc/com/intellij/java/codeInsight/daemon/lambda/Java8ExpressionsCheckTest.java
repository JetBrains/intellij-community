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
import com.intellij.util.containers.ContainerUtil;
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

  public void testForbidCachingForAllQualifiersWhenDependOnThreadLocalTypes() {
    configure();
    PsiMethodCallExpression getKeyCall =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);

    PsiLambdaExpression l1 = PsiTreeUtil.getParentOfType(getKeyCall, PsiLambdaExpression.class);
    PsiLambdaExpression l2 = (PsiLambdaExpression)PsiTreeUtil.skipWhitespacesForward(l1.getNextSibling());

    //ensure chained method calls inside lambda are resolved
    //including entry.getKey()
    //these calls depend on ThreadLocalTypes and should not be cached
    //note that their types should not be cached as well
    l2.getFunctionalInterfaceType();

    //check that getKey was not cached in the line above
    PsiType type = getKeyCall.getType();
    assertEquals(CommonClassNames.JAVA_LANG_STRING, type.getCanonicalText());
  }

  public void testTypeOfThrowsExpression() {
    configure();
    PsiMethodCallExpression fooCall =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);

    assertNotNull(fooCall.getType());
  }
  
  public void testTypeOfDiamonds() {
    configure();
    PsiNewExpression nestedConstructor =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiNewExpression.class);

    assertNotNull(nestedConstructor.resolveConstructor());
  }

  public void testRecursiveApplicabilityCheck() {
    configure();
    PsiMethodCallExpression getDataCall =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);
    assertNotNull(getDataCall);

    //ensure applicability is not called recursively
    assertNotNull(getDataCall.getType());
  }

  public void testRecursiveConflictResolution() {
    configure();
    PsiMethodCallExpression assertEquals =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);
    assertNotNull(assertEquals);

    //ensure conflict check is not called recursively
    assertNotNull(assertEquals.getMethodExpression().advancedResolve(true));
  }

  public void testLambdaParameterTypeDetection() {
    configure();
    PsiReferenceExpression referenceExpression =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiReferenceExpression.class);

    PsiType type = referenceExpression.getType();
    assertTrue(type.getCanonicalText(), type.equalsToText(CommonClassNames.JAVA_LANG_STRING));
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

  public void testLambdaParameterDeterminesNeighbourLambdaType() { 
    doTestParametersSideEffects(); 
  }

  public void testLambdaParameterTypeSideEffects() {
    doTestParametersSideEffects();
  }

  private void doTestParametersSideEffects() {
    configure();
    Collection<PsiParameter> parameters = PsiTreeUtil.findChildrenOfType(getFile(), PsiParameter.class);
    for (PsiParameter parameter : parameters) {
      if (parameter.getTypeElement() == null) { //lambda parameter
        assertNotNull(parameter.getType());
        Collection<PsiCallExpression> expressions = PsiTreeUtil.findChildrenOfType(getFile(), PsiCallExpression.class);
        for (PsiCallExpression expression : expressions) {
          assertNotNull(expression.getText(), expression.resolveMethod());
        }

        dropCaches();
      }
    }
  }

  public void testCachingOfResultsDuringCandidatesIteration() {
    configure();
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
    configure();
    PsiMethodCallExpression callExpression =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);

    assertNotNull(callExpression);
    assertNotNull(callExpression.getType());

    final Collection<PsiCallExpression> methodCallExpressions = PsiTreeUtil.findChildrenOfType(getFile(), PsiCallExpression.class);
    for (PsiCallExpression expression : methodCallExpressions) {
      assertNotNull("Failed inference for: " + expression.getText(), expression.getType());
    }
  }

  private void configure() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
  }

  public void testIDEA140035() {
    doTestAllMethodCallExpressions();
    doTestAllParameterTypes();
  }

  public void testIDEA211775() {
    doTestAllMethodCallExpressions();
    doTestAllParameterTypes();
  }

  private void doTestAllParameterTypes() {
    final Collection<PsiParameter> parameterLists = PsiTreeUtil.findChildrenOfType(getFile(), PsiParameter.class);
    for (PsiParameter parameter : parameterLists) {
      if (parameter.getTypeElement() != null) continue;
      dropCaches();
      final PsiType type = parameter.getType();
      assertFalse("Failed inference for: " + parameter.getParent().getText(), type instanceof PsiLambdaParameterType);
    }
  }

  private void dropCaches() {
    getPsiManager().dropResolveCaches();
  }

  public void testOuterCallOverloads() {
    configure();
    PsiMethodCallExpression innerCall =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);

    PsiMethodCallExpression outerCall = (PsiMethodCallExpression) innerCall.getParent().getParent();

    assertAmbiguous(outerCall);
    assertAmbiguous(innerCall);

    dropCaches();

    assertAmbiguous(innerCall);
    assertAmbiguous(outerCall);
  }

  private static void assertAmbiguous(PsiMethodCallExpression call) {
    assertNull(call.getText(), call.resolveMethod());
    assertSize(2, call.getMethodExpression().multiResolve(false));
    assertNull(call.getText(), call.getType());
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

  public void testResolveDiamondBeforeOuterCall() {
    configure();
    PsiNewExpression newExpression = ContainerUtil.getOnlyItem(PsiTreeUtil.findChildrenOfType(getFile(), PsiNewExpression.class));
    assertNotNull(newExpression);
    PsiType type = newExpression.getType();
    assertEquals("TreeSet<? super java.lang.String>", type.getCanonicalText());
  }

  public void testResolveDiamondReplacementBeforeOuterCall() {
    configure();
    PsiMethodCallExpression innerCall =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);

    assertNotNull(innerCall);
    PsiType type = innerCall.getType();
    assertEquals("TreeSet<? super java.lang.String>", type.getCanonicalText());
  }

  public void testLambdaWithLongChainInReturn() {
    configure();
    PsiExpression innerCall =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);

    assertNotNull(innerCall);
    assertNotNull(innerCall.getType());
  }

  private void doTestAllMethodCallExpressions() {
    configure();
    final Collection<PsiCallExpression> methodCallExpressions = PsiTreeUtil.findChildrenOfType(getFile(), PsiCallExpression.class);
    for (PsiCallExpression expression : methodCallExpressions) {
      dropCaches();
      if (expression instanceof PsiMethodCallExpression) {
        assertNotNull("Failed to resolve: " + expression.getText(), expression.resolveMethod());
      }
      assertNotNull("Failed inference for: " + expression.getText(), expression.getType());
    }

    final Collection<PsiNewExpression> parameterLists = PsiTreeUtil.findChildrenOfType(getFile(), PsiNewExpression.class);
    for (PsiNewExpression newExpression : parameterLists) {
      dropCaches();
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
