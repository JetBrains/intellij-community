package com.intellij.codeInsight.generation.methodsOverridingStatistics;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodWithOverridingPercentMember;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
@SuppressWarnings("unchecked")
public class JavaMethodsOverridingStatisticsTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/generation/javaMethodsOverridingStatistics/";
  }

  public void testMethods() {
    doTest(1, pair("method", 100));
  }

  public void testMethods2() {
    doTest(2, pair("method", 50), pair("method", 100));
  }

  public void testMethods3() {
    doTest(1, pair("method2", 100));
  }

  public void testMethods4() {
    doTest(2, pair("method2", 100), pair("method", 50));
  }

  public void testMethods5() {
    doTest(3, pair("method", 100), pair("method2", 100), pair("method", 33));
  }

  private void doTest(final int resultSize, final Pair<String, Integer>... expectedValues) {
    myFixture.configureByFile(getTestName(false) + ".java");

    final PsiClass contextClass =
      OverrideImplementUtil.getContextClass(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), true);
    assert contextClass != null;

    if (OverrideImplementExploreUtil.getMethodSignaturesToOverride(contextClass).isEmpty() && expectedValues.length != 0) {
      fail();
    }

    final Collection<CandidateInfo> candidateInfos = OverrideImplementExploreUtil.getMethodsToOverrideImplement(contextClass, false);
    final PsiMethodWithOverridingPercentMember[] searchResults = PsiMethodWithOverridingPercentMember
      .calculateOverridingPercents(candidateInfos);
    assertSize(resultSize, searchResults);

    final Set<Pair<String, Integer>> actualValues = new HashSet<>();
    for (PsiMethodWithOverridingPercentMember searchResult : searchResults) {
      actualValues.add(Pair.<String, Integer>create(searchResult.getElement().getName(), searchResult.getOverridingPercent()));
    }

    final Set<Pair<String, Integer>> expectedValuesSet = ContainerUtil.newHashSet(expectedValues);

    assertEquals(expectedValuesSet, actualValues);
  }

  private static Pair<String, Integer> pair(final String methodName, final int percent) {
    return Pair.create(methodName, percent);
  }
}
