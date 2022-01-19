// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.chainsSearch.ChainRelevance;
import com.intellij.compiler.chainsSearch.ChainSearchMagicConstants;
import com.intellij.compiler.chainsSearch.completion.MethodChainCompletionContributor;
import com.intellij.compiler.chainsSearch.completion.lookup.JavaRelevantChainLookupElement;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.SmartList;

import java.io.IOException;
import java.util.List;

@SkipSlowTestLocally
@NeedsIndex.Full
public class MethodChainsCompletionTest extends AbstractCompilerAwareTest {
  private final static String TEST_INDEX_FILE_NAME = "TestIndex.java";
  private final static String TEST_COMPLETION_FILE_NAME = "TestCompletion.java";
  private final static String BEFORE_COMPLETION_FILE = "BeforeCompletion.java";
  private final static String AFTER_COMPLETION_FILE = "AfterCompletion.java";
  private boolean myDefaultAutoCompleteOnCodeCompletion;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    installCompiler();
    Registry.get(MethodChainCompletionContributor.REGISTRY_KEY).setValue(true, myFixture.getTestRootDisposable());
    myDefaultAutoCompleteOnCodeCompletion = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = false;
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
    CompilerReferenceService.getInstanceIfEnabled(getProject());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = myDefaultAutoCompleteOnCodeCompletion;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/methodChains/";
  }

  public void testOneRelevantMethod() throws IOException {
    assertAdvisorLookupElementEquals("e.getProject", 0, 1, 0, assertOneElement(doCompletion()));
  }

  public void testCyclingMethodsNotShowed() throws IOException {
    assertEmpty(doCompletion());
  }

  public void testStaticMethod() throws IOException {
    List<JavaRelevantChainLookupElement> elements = doCompletion();
    assertSize(2, elements);
    assertAdvisorLookupElementEquals("getInstance", 0, 1, 0, elements.get(0));
  }

  public void testStaticMethodAndMethod() throws IOException {
    List<JavaRelevantChainLookupElement> elements = doCompletion();
    assertEquals(String.valueOf(elements), 2, elements.size());
    assertAdvisorLookupElementEquals("findClass", 1, 1, 0, elements.get(1));
    assertAdvisorLookupElementEquals("m.getContainingClass", 0, 1, 0, elements.get(0));
  }

  public void testOneChainContainsOther() throws IOException {
    assertAdvisorLookupElementEquals("p.getBaseDir", 0, 1, 0, assertOneElement(doCompletion()));
  }

  public void _testOneChainContainsOther2() throws IOException {
    assertLookupElementStringEquals(assertOneElement(doCompletion()), "psiElement.getManager");
  }

  public void testTwoVariablesWithOneTypeOrSuperType() throws IOException {
    assertAdvisorLookupElementEquals("c.getProject", 0, 1, 0, assertOneElement(doCompletion()));
  }

  public void testSuperClassMethodsCallings() throws IOException {
    assertAdvisorLookupElementEquals("m.getProject", 0, 1, 0, assertOneElement(doCompletion()));
  }

  public void testMethodsWithParametersInContext() throws IOException {
    assertAdvisorLookupElementEquals("getInstance().findFile().findElementAt", 1, 3, 0, doCompletion().get(0));
  }

  public void testChainsWithIndependentCallings() throws IOException {
    assertSize(2, doCompletion());
  }

  public void _testMethodReturnsSubclassOfTargetClassShowed2() throws IOException {
    assertOneElement(doCompletion());
  }

  public void _testResultsForSuperClassesShowed() throws IOException {
    // if no other elements found we search by super classes
    assertOneElement(doCompletion());
  }

  public void _testInnerClasses() throws IOException {
    assertAdvisorLookupElementEquals("j.getEntry", 0, 1, 0, assertOneElement(doCompletion()));
  }

  public void testMethodsWithSameName() throws IOException {
    assertAdvisorLookupElementEquals("f.createType", 1, 1, 0, assertOneElement(doCompletion()));
  }

  public void testBigrams2() throws IOException {
    List<JavaRelevantChainLookupElement> collection = doCompletion();
    assertAdvisorLookupElementEquals("e.getContainingFile().getVirtualFile", 0, 2, 0, assertOneElement(collection));
  }

  public void _testBigrams3() throws IOException {
    List<JavaRelevantChainLookupElement> elements = doCompletion();
    assertSize(1, elements);
    assertAdvisorLookupElementEquals("getInstance().findFile", 2, 2, 0, elements.get(0));
  }

  public void testMethodWithNoQualifiedVariableInContext() throws IOException {
    assertOneElement(doCompletion());
  }

  public void testMethodIsNotRelevantForField() throws IOException {
    assertOneElement(doCompletion());
  }

  public void testNotRelevantMethodsFilteredInResult() throws IOException {
    assertOneElement(doCompletion());
  }

  public void testGetterInContext() throws IOException {
    assertAdvisorLookupElementEquals("getMyElement().getProject", 0, 1, 0, assertOneElement(doCompletion()));
  }

  public void _testMethodParameterCompletion() throws IOException {
    assertOneElement(doCompletion());
  }

  public void testNoWayToObtainVariableExplicitly() throws IOException {
    assertOneElement(doCompletion());
  }

  public void _testCyclingInstancesObtaining() throws IOException {
    assertEmpty(doCompletion());
  }

  public void testCyclingInstancesObtaining2() throws IOException {
    assertOneElement(doCompletion());
  }

  public void testMethodsWithSameNameWithoutSameParent() throws IOException {
    assertSize(2, doCompletion());
  }

  public void _testResultQualifierNotSameWithTarget() throws IOException {
    assertEmpty(doCompletion());
  }

  public void testPreferGetterToMethodChain() throws IOException {
    compileAndComplete();
    myFixture.assertPreferredCompletionItems(0, "getEditor", "getInstance().getEditor");
  }

  public void testResultOrdering() throws IOException {
    List<JavaRelevantChainLookupElement> lookupElements = doCompletion();
    assertSize(4, lookupElements);
    assertLookupElementStringEquals(lookupElements.get(0), "f.createFileFromText");
    assertLookupElementStringEquals(lookupElements.get(1), "getInstance().getPsiFile");
    assertLookupElementStringEquals(lookupElements.get(2), "getInstance().findFile");
    assertLookupElementStringEquals(lookupElements.get(3), "psiClass.getContainingClass");
  }

  public void testResultRelevance() throws IOException {
    List<JavaRelevantChainLookupElement> javaRelevantChainLookupElements = doCompletion();
    assertSize(1, javaRelevantChainLookupElements);
    //assertEquals("e.getContainingClass", weightableChainLookupElements.get(0).getLookupString());
    assertEquals("getInstance().findClass", javaRelevantChainLookupElements.get(0).getLookupString());
  }

  public void testResultRelevance3() throws IOException {
    List<JavaRelevantChainLookupElement> javaRelevantChainLookupElements = doCompletion();
    assertSize(2, javaRelevantChainLookupElements);
    assertEquals("e.getProject1", javaRelevantChainLookupElements.get(0).getLookupString());
    assertEquals("psiManager.getProject", javaRelevantChainLookupElements.get(1).getLookupString());
  }

  public void testRenderingVariableInContextAndNotInContext() throws IOException {
    doTestRendering();
  }

  public void testRenderingStaticMethods() throws IOException {
    doTestRendering();
  }

  public void testRenderingIntroduceVariable() throws IOException {
    doTestRendering();
  }

  public void testMethodQualifierClass() throws IOException {
    doTestRendering();
  }

  public void testArray() throws IOException {
    JavaRelevantChainLookupElement element = assertOneElement(doCompletion());
    assertEquals("c.getMethods", element.getLookupString());
  }

  public void testCollection() throws IOException {
    JavaRelevantChainLookupElement element = assertOneElement(doCompletion());
    assertEquals("c.getMethods", element.getLookupString());
  }

  public void testReturnStatement() throws IOException {
    JavaRelevantChainLookupElement element = assertOneElement(doCompletion());
    assertEquals("f.createClass", element.getLookupString());
  }

  public void testMethodCallInFieldInitializer() throws IOException {
    doTestRendering();
  }

  public void testDoNotSuggestUninitializedVariable() throws IOException {
    JavaRelevantChainLookupElement element = assertOneElement(doCompletion());
    assertEquals("psiElement.getProject", element.getLookupString());
  }

  public void testChainWithCastOnContextVariable() throws IOException {
    JavaRelevantChainLookupElement element = assertOneElement(doCompletion());
    assertEquals("(EditorEx)editor.getMarkupModel", element.toString());
  }

  public void testChainWithCastOnVariableOutsideContext() throws IOException {
    assertEmpty(doCompletion());
  }

  public void testChainWithCastOnStaticMethod() throws IOException {
    JavaRelevantChainLookupElement element = assertOneElement(doCompletion());
    assertEquals("(InspectionManagerEx)getInstance().createContext", element.toString());
  }

  public void testChainEndedWithCast() throws IOException {
    JavaRelevantChainLookupElement element = assertOneElement(doCompletion());
    assertEquals("(InspectionManagerEx)getInstance", element.toString());
  }

  public void testLongChainWithCast() throws IOException {
    assertEquals("the test should be modified when MAX_CHAIN_SIZE is changed", 4, ChainSearchMagicConstants.MAX_CHAIN_SIZE);
    assertEquals("a.getB().getC().getD", assertOneElement(doCompletion()).toString());
  }

  public void assertAdvisorLookupElementEquals(String lookupText,
                                               int unreachableParametersCount,
                                               int chainSize,
                                               int parameterInContext,
                                               JavaRelevantChainLookupElement actualLookupElement) {
    assertLookupElementStringEquals(actualLookupElement, lookupText);
    assertChainRelevanceEquals(actualLookupElement.getChainRelevance(), chainSize, parameterInContext, unreachableParametersCount);
  }

  private static void assertLookupElementStringEquals(LookupElement lookupElement, String lookupText) {
    assertEquals(lookupText, lookupElement.getLookupString());
  }

  private static void assertChainRelevanceEquals(ChainRelevance chainRelevance,
                                                 int chainSize,
                                                 int parametersInContext,
                                                 int unreachableParametersCount) {
    assertEquals(chainSize, chainRelevance.getChainSize());
    assertEquals(unreachableParametersCount, chainRelevance.getUnreachableParameterCount());
    assertEquals(parametersInContext, chainRelevance.getParametersInContext());
  }

  private void doTestRendering() throws IOException {
    compileAndIndexData(TEST_INDEX_FILE_NAME);
    myFixture.configureByFiles(getBeforeCompletionFilePath());
    for (LookupElement element : myFixture.complete(CompletionType.SMART)) {
      if (element instanceof JavaRelevantChainLookupElement) {
        myFixture.getLookup().setCurrentItem(element);
        myFixture.finishLookup(Lookup.AUTO_INSERT_SELECT_CHAR);
        myFixture.checkResultByFile(getAfterCompletionFilePath());
        return;
      }
    }
    fail("relevant method chain isn't found");
  }

  private List<JavaRelevantChainLookupElement> doCompletion() throws IOException {
    LookupElement[] allLookupElements = compileAndComplete();
    List<JavaRelevantChainLookupElement> targetLookupElements = new SmartList<>();
    for (LookupElement lookupElement : allLookupElements) {
      if (lookupElement instanceof JavaRelevantChainLookupElement) {
        targetLookupElements.add((JavaRelevantChainLookupElement)lookupElement);
      }
    }
    return targetLookupElements;
  }

  private LookupElement[] compileAndComplete() throws IOException {
    compileAndIndexData(TEST_INDEX_FILE_NAME);
    myFixture.configureByFiles(getTestCompletionFilePath());
    LookupElement[] lookupElements = myFixture.complete(CompletionType.SMART);
    return lookupElements == null ? LookupElement.EMPTY_ARRAY : lookupElements;
  }

  private String getTestCompletionFilePath() {
    return getName() + "/" + TEST_COMPLETION_FILE_NAME;
  }

  private String getBeforeCompletionFilePath() {
    return getName() + "/" + BEFORE_COMPLETION_FILE;
  }

  private String getAfterCompletionFilePath() {
    return getName() + "/" + AFTER_COMPLETION_FILE;
  }
}
