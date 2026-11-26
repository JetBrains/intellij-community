// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromMethodReferenceFix;
import com.intellij.modcommand.ActionContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiDiamondType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLambdaParameterType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class Java8ExpressionsCheckTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/expressions";

  @Test
  public void testSecondConflictResolutionOnSameMethodCall() {
    doTestAllMethodCallExpressions();
  }

  @Test
  public void testNestedLambdaAdditionalConstraints() {
    doTestAllMethodCallExpressions();
  }

  @Test
  public void testPolyExpressionOnRSideOfAssignment() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression
        call = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                           PsiMethodCallExpression.class);
      assertFalse(call.resolveMethodGenerics().isValidResult());
    });
  }

  @Test
  public void testCreateMethodFromMethodReferenceAvailability() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiFile file = myFixture.getFile();
      Editor editor = myFixture.getEditor();
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      PsiMethodReferenceExpression methodReference = PsiTreeUtil.getParentOfType(element, PsiMethodReferenceExpression.class);
      assertNotNull(new CreateMethodFromMethodReferenceFix(methodReference).getPresentation(ActionContext.from(editor, file)));
    });
  }


  @Test
  public void testMethodApplicability() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiFile file = myFixture.getFile();
      Editor editor = myFixture.getEditor();
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      assertNotNull(expression.getType());
    });
  }

  @Test
  public void testMethodApplicability1() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(BASE_PATH + "/MethodApplicability.java");
    ReadAction.runBlocking(() -> {
      PsiFile file = myFixture.getFile();
      Editor editor = myFixture.getEditor();
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      for (JavaResolveResult result : expression.multiResolve(true)) {
        assertFalse(result.isValidResult());
      }
    });
  }

  @Test
  public void testDiamondInsideOverloadResolution() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(BASE_PATH + "/" + myFixture.getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      PsiFile file = myFixture.getFile();
      Editor editor = myFixture.getEditor();
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      PsiMethodCallExpression expression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      assertTrue(expression.resolveMethodGenerics().isValidResult());
    });
  }

  @Test
  public void testNestedLambdaReturnTypeCheck() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression
        call = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                           PsiMethodCallExpression.class);
      @Nullable PsiLambdaExpression lambda = PsiTreeUtil.getParentOfType(call, PsiLambdaExpression.class);

      PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(lambda.getFunctionalInterfaceType());
      Map<PsiElement, @Nls String> errors = LambdaUtil.checkReturnTypeCompatible(lambda, interfaceReturnType);
      if (errors != null) {
        fail(StreamEx.of(errors.values()).joining(", "));
      }

      PsiType type = call.getType();
      assertNotNull(type);
      assertFalse(type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT), type.getPresentableText());
    });
  }

  @Test
  public void testPermutedExpressionsInList() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    @NonNls String filePath = BASE_PATH + "/" + myFixture.getTestName(false) + ".java";
    daemonFixture.get().configureByFile(filePath);
    PsiExpressionList list = ReadAction.computeBlocking(
      () -> PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                        PsiExpressionList.class));
    PsiExpression arg = ReadAction.computeBlocking(() -> list.getExpressions()[7]);
    assertEquals(1, daemonFixture.get().highlightErrors().size());
    assertEquals(arg, ReadAction.computeBlocking(() -> list.getExpressions()[7]));
  }

  @Test
  public void testForbidCachingForAllQualifiersWhenDependOnThreadLocalTypes() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression getKeyCall =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                    PsiMethodCallExpression.class);

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
    });
  }

  @Test
  public void testTypeOfThrowsExpression() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression fooCall =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                    PsiMethodCallExpression.class);

      assertNotNull(fooCall.getType());
    });
  }

  @Test
  public void testTypeOfDiamonds() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiNewExpression nestedConstructor =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                    PsiNewExpression.class);

      assertNotNull(nestedConstructor.resolveConstructor());
    });
  }

  @Test
  public void testRecursiveApplicabilityCheck() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression getDataCall =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                    PsiMethodCallExpression.class);
      assertNotNull(getDataCall);

      //ensure applicability is not called recursively
      assertNotNull(getDataCall.getType());
    });
  }

  @Test
  public void testRecursiveConflictResolution() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression assertEquals =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                    PsiMethodCallExpression.class);
      assertNotNull(assertEquals);

      //ensure conflict check is not called recursively
      assertNotNull(assertEquals.getMethodExpression().advancedResolve(true));
    });
  }

  @Test
  public void testLambdaParameterTypeDetection() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiReferenceExpression referenceExpression =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                    PsiReferenceExpression.class);

      PsiType type = referenceExpression.getType();
      assertTrue(type.equalsToText(CommonClassNames.JAVA_LANG_STRING), type.getCanonicalText());
    });
  }

  @Test
  public void testAvoidClassRefCachingDuringInference() {
    doTestAllMethodCallExpressions();
  }

  @Test
  public void testInfinitiveParameterBoundsCheck() {
    doTestAllMethodCallExpressions();
  }

  @Test
  public void testProoveThatInferenceInsideLambdaBodyDontInfluenceOuterCallInference() {
    doTestAllMethodCallExpressions();
  }

  @Test
  public void testDontCollectUnhandledReferencesInsideLambdaBody() {
    doTestAllMethodCallExpressions();
  }

  @Test
  public void testCachedUnresolvedMethods() {
    doTestCachedUnresolved();
  }

  @Test
  public void testCacheUnresolvedMethods2() {
    doTestCachedUnresolved();
  }

  @Test
  public void testCacheUnresolvedMethods3() {
    doTestCachedUnresolved();
  }

  @Test
  public void testCacheUnresolvedMethods4() {
    doTestCachedUnresolved();
  }

  @Test
  public void testCacheUnresolvedMethods5() {
    doTestCachedUnresolved();
  }

  @Test
  public void testMethodOverloadsInsideLambdaHierarchy() {
    doTestAllMethodCallExpressions();
  }

  @Test
  public void testObjectOverloadsWithDiamondsOverMultipleConstructors() {
    doTestAllMethodCallExpressions();
  }

  @Test
  public void testLambdaParameterDeterminesNeighbourLambdaType() {
    doTestParametersSideEffects();
  }

  @Test
  public void testLambdaParameterTypeSideEffects() {
    doTestParametersSideEffects();
  }

  private void doTestParametersSideEffects() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      Collection<PsiParameter> parameters = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiParameter.class);
      for (PsiParameter parameter : parameters) {
        if (parameter.getTypeElement() == null) { //lambda parameter
          assertNotNull(parameter.getType());
          Collection<PsiCallExpression> expressions = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiCallExpression.class);
          for (PsiCallExpression expression : expressions) {
            assertNotNull(expression.resolveMethod(), expression.getText());
          }

          dropCaches();
        }
      }
    });
  }

  @Test
  public void testCachingOfResultsDuringCandidatesIteration() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      final Collection<PsiMethodCallExpression> methodCallExpressions =
        PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiMethodCallExpression.class);

      final PsiResolveHelper helper = JavaPsiFacade.getInstance(myFixture.getProject()).getResolveHelper();
      for (PsiMethodCallExpression expression : methodCallExpressions) {
        CandidateInfo[] candidates = helper.getReferencedMethodCandidates(expression, false, true);
        PsiExpressionList argumentList = expression.getArgumentList();
        PsiExpression[] args = argumentList.getExpressions();
        for (JavaResolveResult result : candidates) {
          if (result instanceof MethodCandidateInfo info) {
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
    });
  }

  @Test
  public void testNonCachingFolding() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    final String filePath = BASE_PATH + "/" + myFixture.getTestName(false) + ".java";
    daemonFixture.get().configureByFile(filePath);
    ReadAction.runBlocking(() -> {
      PsiNewExpression newWithAnonym =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                    PsiNewExpression.class);
      ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes(newWithAnonym, false);
      assertNotNull(types);
    });

    daemonFixture.get().doTestConfiguredFile(false, false, filePath);
  }

  @Test
  public void testRejectCachedTopLevelSessionIfItCorrespondsToTheWrongOverload() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    final String filePath = BASE_PATH + "/" + myFixture.getTestName(false) + ".java";
    daemonFixture.get().configureByFile(filePath);
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression methodCall =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                    PsiMethodCallExpression.class);
      assertNotNull(methodCall);
      final PsiResolveHelper helper = JavaPsiFacade.getInstance(methodCall.getProject()).getResolveHelper();
      CandidateInfo[] candidates = helper.getReferencedMethodCandidates(methodCall, false, true);
      for (CandidateInfo candidate : candidates) {
        if (candidate instanceof MethodCandidateInfo) {
          //try to cache top level session
          //noinspection ResultOfMethodCallIgnored
          candidate.getSubstitutor();
        }
      }
    });

    daemonFixture.get().doTestConfiguredFile(false, false, filePath);
  }

  @Test
  public void testCheckedExceptionConstraintToTopLevel() {
    doTestCachedUnresolved();
  }

  private void doTestCachedUnresolved() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression callExpression =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                    PsiMethodCallExpression.class);

      assertNotNull(callExpression);
      assertNotNull(callExpression.getType());

      final Collection<PsiCallExpression> methodCallExpressions = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiCallExpression.class);
      for (PsiCallExpression expression : methodCallExpressions) {
        assertNotNull(expression.getType(), "Failed inference for: " + expression.getText());
      }
    });
  }

  private void configure() {
    daemonFixture.get().configureByFile(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java");
  }

  @Test
  public void testIDEA140035() {
    doTestAllMethodCallExpressions();
    doTestAllParameterTypes();
  }

  @Test
  public void testIDEA211775() {
    doTestAllMethodCallExpressions();
    doTestAllParameterTypes();
  }

  private void doTestAllParameterTypes() {
    ReadAction.runBlocking(() -> {
      final Collection<PsiParameter> parameterLists =
        PsiTreeUtil.findChildrenOfType(daemonFixture.get().getFixture().getFile(), PsiParameter.class);
      for (PsiParameter parameter : parameterLists) {
        if (parameter.getTypeElement() != null) continue;
        dropCaches();
        final PsiType type = parameter.getType();
        assertFalse(type instanceof PsiLambdaParameterType, "Failed inference for: " + parameter.getParent().getText());
      }
    });
  }

  private void dropCaches() {
    PsiManager.getInstance(daemonFixture.get().getFixture().getProject()).dropResolveCaches();
  }

  @Test
  public void testOuterCallOverloads() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression innerCall =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                    PsiMethodCallExpression.class);

      PsiMethodCallExpression outerCall = (PsiMethodCallExpression) innerCall.getParent().getParent();

      assertAmbiguous(outerCall, "java.util.Collection<? extends java.lang.Object>");
      assertAmbiguous(innerCall, "java.util.Collection<? extends java.lang.Object>");

      dropCaches();

      assertAmbiguous(innerCall, "java.util.Collection<? extends java.lang.Object>");
      assertAmbiguous(outerCall, "java.util.Collection<? extends java.lang.Object>");
    });
  }

  private static void assertAmbiguous(PsiMethodCallExpression call, @Nullable String expectedType) {
    assertNull(call.resolveMethod(), call.getText());
    assertEquals(2, call.multiResolve(false).length);
    PsiType type = call.getType();
    if (expectedType == null) {
      assertNull(type, call.getText());
    } else {
      assertEquals(expectedType, type.getCanonicalText(), call.getText());
    }
  }

  @Test
  public void testAdditionalConstraintsBasedOnLambdaResolution() {
    doTestAllMethodCallExpressions();
  }

  @Test
  public void testAdditionalConstraintsBasedOnLambdaResolutionForNestedLambdas() {
    doTestAllMethodCallExpressions();
  }

  @Test
  public void testOverloadResolutionInsideLambdaInsideNestedCall() {
    doTestAllMethodCallExpressions();
  }

  @Test
  public void testResolveDiamondBeforeOuterCall() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiNewExpression newExpression = ContainerUtil.getOnlyItem(PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiNewExpression.class));
      assertNotNull(newExpression);
      PsiType type = newExpression.getType();
      assertEquals("TreeSet<? super java.lang.String>", type.getCanonicalText());
    });
  }

  @Test
  public void testResolveDiamondReplacementBeforeOuterCall() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression innerCall =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                    PsiMethodCallExpression.class);

      assertNotNull(innerCall);
      PsiType type = innerCall.getType();
      assertEquals("TreeSet<? super java.lang.String>", type.getCanonicalText());
    });
  }

  @Test
  public void testLambdaWithLongChainInReturn() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      PsiExpression innerCall =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                    PsiMethodCallExpression.class);

      assertNotNull(innerCall);
      assertNotNull(innerCall.getType());
    });
  }

  private void doTestAllMethodCallExpressions() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    configure();
    ReadAction.runBlocking(() -> {
      final Collection<PsiCallExpression> methodCallExpressions = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiCallExpression.class);
      for (PsiCallExpression expression : methodCallExpressions) {
        dropCaches();
        if (expression instanceof PsiMethodCallExpression) {
          assertNotNull(expression.resolveMethod(), "Failed to resolve: " + expression.getText());
        }
        assertNotNull(expression.getType(), "Failed inference for: " + expression.getText());
      }

      final Collection<PsiNewExpression> parameterLists = PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiNewExpression.class);
      for (PsiNewExpression newExpression : parameterLists) {
        dropCaches();
        final PsiType[] arguments = newExpression.getTypeArguments();
        String failMessage = "Failed inference for: " + newExpression.getParent().getText();
        assertNotNull(arguments, failMessage);
        PsiDiamondType diamondType = PsiDiamondType.getDiamondType(newExpression);
        if (diamondType != null) {
          JavaResolveResult staticFactory = diamondType.getStaticFactory();
          assertNotNull(staticFactory);
          assertInstanceOf(MethodCandidateInfo.class, staticFactory);
          assertNull(((MethodCandidateInfo)staticFactory).getInferenceErrorMessage(), failMessage);
        }
      }
    });
  }
}
