package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.methodChains.completion.MethodsChainsCompletionContributor;
import com.intellij.codeInsight.completion.methodChains.completion.lookup.ChainCompletionMethodCallLookupElement;
import com.intellij.codeInsight.completion.methodChains.completion.lookup.WeightableChainLookupElement;
import com.intellij.codeInsight.completion.methodChains.search.ChainRelevance;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.compilerOutputIndex.api.fs.FileVisitorService;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputIndexFeature;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputIndexer;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.util.SmartList;

import java.io.File;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class MethodChainsCompletionTest extends AbstractCompilerAwareTest {
  private final static String TEST_INDEX_FILE_NAME = "TestIndex.java";
  private final static String TEST_COMPLETION_FILE_NAME = "TestCompletion.java";

  private final static String BEFORE_COMPLETION_FILE = "BeforeCompletion.java";
  private final static String AFTER_COMPLETION_FILE = "AfterCompletion.java";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CompilerOutputIndexFeature.METHOD_CHAINS_COMPLETION.enable();
  }

  @Override
  protected void tearDown() throws Exception {
    CompilerOutputIndexFeature.METHOD_CHAINS_COMPLETION.disable();
    super.tearDown();
  }

  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/methodChains/";
  }

  public void testOneRelevantMethod() {
    assertAdvisorLookupElementEquals("e.getProject", 0, 8, 1, 0, assertOneElement(doCompletion()));
  }

  public void testCyclingMethodsNotShowed() {
    assertEmpty(doCompletion());
  }

  public void testStaticMethod() {
    final List<WeightableChainLookupElement> elements = doCompletion();
    assertAdvisorLookupElementEquals("getInstance", 0, 2, 1, 0, assertOneElement(elements));
  }

  public void testStaticMethodAndMethod() {
    final List<WeightableChainLookupElement> elements = doCompletion();
    assertEquals(String.valueOf(elements), elements.size(), 2);
    assertAdvisorLookupElementEquals("findClass", 0, 3, 1, 1, elements.get(1));
    assertAdvisorLookupElementEquals("m.getContainingClass", 0, 5, 1, 0, elements.get(0));
  }

  public void testOneChainContainsOther() {
    assertAdvisorLookupElementEquals("p.getBaseDir", 0, 8, 1, 0, assertOneElement(doCompletion()));
  }

  public void testOneChainContainsOther2() {
    assertLookupElementStringEquals(assertOneElement(doCompletion()), "getManager");
  }

  public void testTwoVariablesWithOneTypeOrSuperType() {
    assertAdvisorLookupElementEquals("c.getProject", 0, 4, 1, 0, assertOneElement(doCompletion()));
  }

  public void testSuperClassMethodsCallings() {
    assertAdvisorLookupElementEquals("m.getProject", 0, 8, 1, 0, assertOneElement(doCompletion()));
  }

  public void testMethodsWithParametersInContext() {
    assertAdvisorLookupElementEquals("getInstance().findFile().findElementAt", 0, 4, 3, 0, assertOneElement(doCompletion()));
  }

  public void _testChainsWithIndependentCallings() {
    assertOneElement(doCompletion());
  }

  public void testMethodReturnsSubclassOfTargetClassNotShowed2() {
    assertEmpty(doCompletion());
  }

  public void testResultsForSuperClassesNotShowed() {
    assertEmpty(doCompletion());
  }

  public void testInnerClasses() {
    assertAdvisorLookupElementEquals("j.getEntry", 0, 8, 1, 0, assertOneElement(doCompletion()));
  }

  public void testMethodsWithSameName() {
    assertAdvisorLookupElementEquals("f.createType", 1, 8, 1, 0, assertOneElement(doCompletion()));
  }

  public void testBigrams2() {
    final List<WeightableChainLookupElement> collection = doCompletion();
    assertAdvisorLookupElementEquals("e.getContainingFile().getVirtualFile", 0, 8, 1, 0, assertOneElement(collection));
  }

  public void testBigrams3() {
    final List<WeightableChainLookupElement> collection = doCompletion();
    assertAdvisorLookupElementEquals("getInstance().findFile().findElementAt", 2, 8, 3, 0, assertOneElement(collection));
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
    assertAdvisorLookupElementEquals("getMyElement().getProject", 0, 8, 1, 0, assertOneElement(doCompletion()));
  }

  public void testMethodParameterCompletion() {
    assertOneElement(doCompletion());
  }

  public void testNoWayToObtainVariableExplicitly() {
    assertOneElement(doCompletion());
  }

  public void testCyclingInstancesObtaining() {
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

  public void testResultOrdering() {
    final List<WeightableChainLookupElement> lookupElements = doCompletion();
    assertSize(4, lookupElements);
    assertLookupElementStringEquals(lookupElements.get(0), "f.createFileFromText");
    assertLookupElementStringEquals(lookupElements.get(1), "getInstance().findFile");
    assertLookupElementStringEquals(lookupElements.get(2), "getInstance().getPsiFile");
    assertLookupElementStringEquals(lookupElements.get(3), "getContainingClass");
  }

  public void testResultRelevance() {
    final List<WeightableChainLookupElement> weightableChainLookupElements = doCompletion();
    assertEquals("e.getContainingClass", weightableChainLookupElements.get(0).getLookupString());
    assertEquals("getInstance().findClass", weightableChainLookupElements.get(1).getLookupString());
  }

  public void testResultRelevance3() {
    final List<WeightableChainLookupElement> weightableChainLookupElements = doCompletion();
    assertSize(2, weightableChainLookupElements);
    assertEquals("e.getProject1", weightableChainLookupElements.get(0).getLookupString());
    assertEquals("getProject", weightableChainLookupElements.get(1).getLookupString());
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

  public void assertAdvisorLookupElementEquals(final String lookupText,
                                               final int unreachableParametersCount,
                                               final int lastMethodWeight,
                                               final int chainSize,
                                               final int notMatchedStringVars,
                                               final WeightableChainLookupElement actualLookupElement) {
    assertLookupElementStringEquals(actualLookupElement, lookupText);
    assertChainRelevanceEquals(actualLookupElement.getChainRelevance(), lastMethodWeight, chainSize, notMatchedStringVars,
                               unreachableParametersCount);
  }

  private static void assertLookupElementStringEquals(final LookupElement lookupElement, final String lookupText) {
    assertEquals(lookupText, lookupElement.getLookupString());
  }

  private static void assertChainRelevanceEquals(final ChainRelevance chainRelevance,
                                                 final int lastMethodWeight,
                                                 final int chainSize,
                                                 final int notMatchedStringVars,
                                                 final int unreachableParametersCount) {
    assertEquals(notMatchedStringVars, chainRelevance.getNotMatchedStringVars());
    assertEquals(chainSize, chainRelevance.getChainSize());
    assertEquals(unreachableParametersCount, chainRelevance.getUnreachableParametersCount());
    assertEquals(lastMethodWeight, chainRelevance.getLastMethodOccurrences());
  }

  private void doTestRendering() {
    PropertiesComponent.getInstance(getProject()).setValue(ChainCompletionMethodCallLookupElement.PROP_METHODS_CHAIN_COMPLETION_AUTO_COMPLETION, String.valueOf(true));
    indexCompiledData(compileData(getName(), TEST_INDEX_FILE_NAME));
    myFixture.configureByFiles(getBeforeCompletionFilePath());
    myFixture.complete(CompletionType.BASIC, MethodsChainsCompletionContributor.INVOCATIONS_THRESHOLD);
    PropertiesComponent.getInstance(getProject()).setValue(ChainCompletionMethodCallLookupElement.PROP_METHODS_CHAIN_COMPLETION_AUTO_COMPLETION, String.valueOf(false));
    myFixture.checkResultByFile(getAfterCompletionFilePath());
  }

  private List<WeightableChainLookupElement> doCompletion() {
    try {
      indexCompiledData(compileData(getName(), TEST_INDEX_FILE_NAME));

      final LookupElement[] allLookupElements = runCompletion();
      final List<WeightableChainLookupElement> targetLookupElements = new SmartList<WeightableChainLookupElement>();
      for (final LookupElement lookupElement : allLookupElements) {
        if (lookupElement instanceof WeightableChainLookupElement) {
          targetLookupElements.add((WeightableChainLookupElement)lookupElement);
        }
      }

      return targetLookupElements;
    }
    finally {
      final CompilerOutputIndexer indexer = CompilerOutputIndexer.getInstance(getProject());
      indexer.projectClosed();
      indexer.removeIndexes();
    }
  }

  private LookupElement[] runCompletion() {
    myFixture.configureByFiles(getTestCompletionFilePath());
    final LookupElement[] lookupElements = myFixture.complete(CompletionType.BASIC, MethodsChainsCompletionContributor.INVOCATIONS_THRESHOLD);
    return lookupElements == null ? LookupElement.EMPTY_ARRAY : lookupElements;
  }

  private void indexCompiledData(final File compilerOutput) {
    final FileVisitorService.DirectoryClassFiles visitorService = new FileVisitorService.DirectoryClassFiles(compilerOutput);
    final CompilerOutputIndexer indexer = CompilerOutputIndexer.getInstance(getProject());
    indexer.projectOpened();
    indexer.clear();
    indexer.reindex(visitorService, new MockProgressIndicator());
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
