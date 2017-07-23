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
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.compiler.chainsSearch.ChainRelevance;
import com.intellij.compiler.chainsSearch.completion.MethodChainCompletionContributor;
import com.intellij.compiler.chainsSearch.completion.lookup.JavaRelevantChainLookupElement;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.util.SmartList;

import java.util.List;

@SkipSlowTestLocally
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
  }

  @Override
  public void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = myDefaultAutoCompleteOnCodeCompletion;
    } finally {
      super.tearDown();
    }
  }

  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/methodChains/";
  }

  public void testOneRelevantMethod() {
    assertAdvisorLookupElementEquals("e.getProject", 0, 1, 0, assertOneElement(doCompletion()));
  }

  public void testCyclingMethodsNotShowed() {
    assertEmpty(doCompletion());
  }

  public void testStaticMethod() {
    List<JavaRelevantChainLookupElement> elements = doCompletion();
    assertSize(2, elements);
    assertAdvisorLookupElementEquals("getInstance", 0, 1, 0, elements.get(0));
  }

  public void testStaticMethodAndMethod() {
    List<JavaRelevantChainLookupElement> elements = doCompletion();
    assertEquals(String.valueOf(elements), 2, elements.size());
    assertAdvisorLookupElementEquals("findClass", 1, 1, 0, elements.get(1));
    assertAdvisorLookupElementEquals("m.getContainingClass", 0, 1, 0, elements.get(0));
  }

  public void testOneChainContainsOther() {
    assertAdvisorLookupElementEquals("p.getBaseDir", 0, 1, 0, assertOneElement(doCompletion()));
  }

  public void _testOneChainContainsOther2() {
    assertLookupElementStringEquals(assertOneElement(doCompletion()), "psiElement.getManager");
  }

  public void testTwoVariablesWithOneTypeOrSuperType() {
    assertAdvisorLookupElementEquals("c.getProject", 0, 1, 0, assertOneElement(doCompletion()));
  }

  public void testSuperClassMethodsCallings() {
    assertAdvisorLookupElementEquals("m.getProject", 0, 1, 0, assertOneElement(doCompletion()));
  }

  public void testMethodsWithParametersInContext() {
    assertAdvisorLookupElementEquals("getInstance().findFile().findElementAt", 1, 3, 0, doCompletion().get(0));
  }

  public void testChainsWithIndependentCallings() {
    assertSize(2, doCompletion());
  }

  public void _testMethodReturnsSubclassOfTargetClassShowed2() {
    assertOneElement(doCompletion());
  }

  public void _testResultsForSuperClassesShowed() {
    // if no other elements found we search by super classes
    assertOneElement(doCompletion());
  }

  public void _testInnerClasses() {
    assertAdvisorLookupElementEquals("j.getEntry", 0, 1, 0, assertOneElement(doCompletion()));
  }

  public void testMethodsWithSameName() {
    assertAdvisorLookupElementEquals("f.createType", 1, 1, 0, assertOneElement(doCompletion()));
  }

  public void testBigrams2() {
    List<JavaRelevantChainLookupElement> collection = doCompletion();
    assertAdvisorLookupElementEquals("e.getContainingFile().getVirtualFile", 0, 2, 0, assertOneElement(collection));
  }

  public void _testBigrams3() {
    List<JavaRelevantChainLookupElement> elements = doCompletion();
    assertSize(1, elements);
    assertAdvisorLookupElementEquals("getInstance().findFile", 2, 2, 0, elements.get(0));
  }

  public void testMethodWithNoQualifiedVariableInContext() {
    assertOneElement(doCompletion());
  }

  public void testMethodIsNotRelevantForField() {
    assertOneElement(doCompletion());
  }

  public void testNotRelevantMethodsFilteredInResult() {
    assertOneElement(doCompletion());
  }

  public void testGetterInContext() {
    assertAdvisorLookupElementEquals("getMyElement().getProject", 0, 1, 0, assertOneElement(doCompletion()));
  }

  public void _testMethodParameterCompletion() {
    assertOneElement(doCompletion());
  }

  public void testNoWayToObtainVariableExplicitly() {
    assertOneElement(doCompletion());
  }

  public void _testCyclingInstancesObtaining() {
    assertEmpty(doCompletion());
  }

  public void testCyclingInstancesObtaining2() {
    assertOneElement(doCompletion());
  }

  public void testMethodsWithSameNameWithoutSameParent() {
    assertSize(2, doCompletion());
  }

  public void testResultQualifierNotSameWithTarget() {
    assertEmpty(doCompletion());
  }

  public void testPreferGetterToMethodChain() {
    compileAndComplete();
    myFixture.assertPreferredCompletionItems(0, "getEditor", "getInstance().getEditor");
  }

  public void testResultOrdering() {
    List<JavaRelevantChainLookupElement> lookupElements = doCompletion();
    assertSize(4, lookupElements);
    assertLookupElementStringEquals(lookupElements.get(0), "f.createFileFromText");
    assertLookupElementStringEquals(lookupElements.get(1), "getInstance().getPsiFile");
    assertLookupElementStringEquals(lookupElements.get(2), "getInstance().findFile");
    assertLookupElementStringEquals(lookupElements.get(3), "psiClass.getContainingClass");
  }

  public void testResultRelevance() {
    List<JavaRelevantChainLookupElement> javaRelevantChainLookupElements = doCompletion();
    assertSize(1, javaRelevantChainLookupElements);
    //assertEquals("e.getContainingClass", weightableChainLookupElements.get(0).getLookupString());
    assertEquals("getInstance().findClass", javaRelevantChainLookupElements.get(0).getLookupString());
  }

  public void testResultRelevance3() {
    List<JavaRelevantChainLookupElement> javaRelevantChainLookupElements = doCompletion();
    assertSize(2, javaRelevantChainLookupElements);
    assertEquals("e.getProject1", javaRelevantChainLookupElements.get(0).getLookupString());
    assertEquals("psiManager.getProject", javaRelevantChainLookupElements.get(1).getLookupString());
  }

  public void testRenderingVariableInContextAndNotInContext() {
    doTestRendering();
  }

  public void testRenderingStaticMethods() {
    doTestRendering();
  }

  public void testRenderingIntroduceVariable() {
    doTestRendering();
  }

  public void testMethodQualifierClass() {
    doTestRendering();
  }

  public void testArray() {
    JavaRelevantChainLookupElement element = assertOneElement(doCompletion());
    assertEquals("c.getMethods", element.getLookupString());
  }

  public void testCollection() {
    JavaRelevantChainLookupElement element = assertOneElement(doCompletion());
    assertEquals("c.getMethods", element.getLookupString());
  }

  public void testReturnStatement() {
    JavaRelevantChainLookupElement element = assertOneElement(doCompletion());
    assertEquals("f.createClass", element.getLookupString());
  }

  public void testMethodCallInFieldInitializer() {
    doTestRendering();
  }

  public void testDoNotSuggestUninitializedVariable() {
    JavaRelevantChainLookupElement element = assertOneElement(doCompletion());
    assertEquals("psiElement.getProject", element.getLookupString());
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

  private void doTestRendering() {
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
    fail("relevant method chain isn't foun");
  }

  private List<JavaRelevantChainLookupElement> doCompletion() {
    LookupElement[] allLookupElements = compileAndComplete();
    List<JavaRelevantChainLookupElement> targetLookupElements = new SmartList<>();
    for (LookupElement lookupElement : allLookupElements) {
      if (lookupElement instanceof JavaRelevantChainLookupElement) {
        targetLookupElements.add((JavaRelevantChainLookupElement)lookupElement);
      }
    }
    return targetLookupElements;
  }

  private LookupElement[] compileAndComplete() {
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
