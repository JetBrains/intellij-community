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
package com.intellij.java.codeInsight.generation.methodsOverridingStatistics;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodWithOverridingPercentMember;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
@SuppressWarnings("unchecked")
@TestApplication
public class JavaMethodsOverridingStatisticsTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/generation/javaMethodsOverridingStatistics/");

  private String testName;

  @BeforeEach
  void setUpTestName(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
  }

  @Test
  public void testMethods() {
    doTest(1, pair("method", 100));
  }

  @Test
  public void testMethods2() {
    doTest(2, pair("method", 50), pair("method", 100));
  }

  @Test
  public void testMethods3() {
    doTest(1, pair("method2", 100));
  }

  @Test
  public void testMethods4() {
    doTest(2, pair("method2", 100), pair("method", 50));
  }

  @Test
  public void testMethods5() {
    doTest(3, pair("method", 100), pair("method2", 100), pair("method", 33));
  }

  private void doTest(final int resultSize, final Pair<String, Integer>... expectedValues) {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + ".java");

    ReadAction.runBlocking(() -> {
      final PsiClass contextClass =
        OverrideImplementUtil.getContextClass(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), true);
      assert contextClass != null;

      if (OverrideImplementExploreUtil.getMethodSignaturesToOverride(contextClass).isEmpty() && expectedValues.length != 0) {
        fail();
      }

      final Collection<CandidateInfo> candidateInfos = OverrideImplementExploreUtil.getMethodsToOverrideImplement(contextClass, false);
      final PsiMethodWithOverridingPercentMember[] searchResults = PsiMethodWithOverridingPercentMember
        .calculateOverridingPercents(candidateInfos);
      assertEquals(resultSize, searchResults.length);

      final Set<Pair<String, Integer>> actualValues = new HashSet<>();
      for (PsiMethodWithOverridingPercentMember searchResult : searchResults) {
        actualValues.add(Pair.create(searchResult.getElement().getName(), searchResult.getOverridingPercent()));
      }

      final Set<Pair<String, Integer>> expectedValuesSet = ContainerUtil.newHashSet(expectedValues);

      assertEquals(expectedValuesSet, actualValues);
    });
  }

  private static Pair<String, Integer> pair(final String methodName, final int percent) {
    return Pair.create(methodName, percent);
  }
}
