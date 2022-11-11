// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromMethodReferenceFix;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class Java8ExpressionsCheckTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/expressions";

  public void testSecondConflictResolutionOnSameMethodCall() {
    doTestAllMethodCallExpressions();
  }

  public void testNestedLambdaAdditionalConstraints() {
    doTestAllMethodCallExpressions();
  }

  public void testPolyExpressionOnRSideOfAssignment() {
    configure();
    PsiMethodCallExpression
      call = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);
    assertFalse(call.resolveMethodGenerics().isValidResult());
  }

  public void testCreateMethodFromMethodReferenceAvailability() {
    configure();
    PsiFile file = getFile();
    Editor editor = getEditor();
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PsiMethodReferenceExpression methodReference = PsiTreeUtil.getParentOfType(element, PsiMethodReferenceExpression.class);
    assertTrue(new CreateMethodFromMethodReferenceFix(methodReference).isAvailable(getProject(), editor, file));
  }


  public void testMethodApplicability() {
    configure();
    PsiFile file = getFile();
    Editor editor = getEditor();
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    assertNotNull(expression.getType());
  }
  
  public void testMethodApplicability1() {
    configureByFile(BASE_PATH + "/MethodApplicability.java");
    PsiFile file = getFile();
    Editor editor = getEditor();
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    for (JavaResolveResult result : expression.getMethodExpression().multiResolve(true)) {
      assertFalse(result.isValidResult());
    }
  }

  public void testDiamondInsideOverloadResolution() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    PsiFile file = getFile();
    Editor editor = getEditor();
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    assertTrue(expression.resolveMethodGenerics().isValidResult());
  }
  
  public void testNestedLambdaReturnTypeCheck() {
    configure();
    PsiMethodCallExpression
      call = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);
    @Nullable PsiLambdaExpression lambda = PsiTreeUtil.getParentOfType(call, PsiLambdaExpression.class);

    PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(lambda.getFunctionalInterfaceType());
    Map<PsiElement, @Nls String> errors = LambdaUtil.checkReturnTypeCompatible(lambda, interfaceReturnType);
    if (errors != null) {
      fail(StreamEx.of(errors.values()).joining(", "));
    }

    PsiType type = call.getType();
    assertNotNull(type);
    assertFalse(type.getPresentableText(), type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT));
  }

  public void testPermutedExpressionsInList() {
    @NonNls String filePath = BASE_PATH + "/" + getTestName(false) + ".java";
    configureByFile(filePath);
    PsiExpressionList list =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpressionList.class);
    PsiExpression arg = list.getExpressions()[7];
    assertEquals(1, highlightErrors().size());
    assertEquals(arg, list.getExpressions()[7]);
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
