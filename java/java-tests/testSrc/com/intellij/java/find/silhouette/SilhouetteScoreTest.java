// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.find.silhouette;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.find.findUsages.similarity.SilhouetteScore;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;


@TestApplication
public class SilhouetteScoreTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    PsiTestUtil.removeAllRoots(fixture.getModule(), IdeaTestUtil.getMockJdk18());
    fixture.createTestProjectStructure(getTestRoot());
  }

  private @NotNull String getTestRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/findSimilar/silhouetteScore/" + heavyFixture.get().getTestName(true);
  }

  public void doTest(String className, String methodName, double finalValue){
    PsiMethod streamMethod = getMethod(className, methodName);
    final JavaFindUsagesHandlerFactory factory = JavaFindUsagesHandlerFactory.getInstance(heavyFixture.get().getProject());
    JavaFindUsagesHandler handler = new JavaFindUsagesHandler(streamMethod, factory);
    ClusteringSearchSession session = new ClusteringSearchSession();
    handler.processElementUsages(streamMethod, info -> {
      try {
        ReadAction.nonBlocking(() -> UsageInfoToUsageConverter.convertToSimilarUsage(new PsiMethod[]{streamMethod}, info, session)).submit(
          AppExecutorUtil.getAppExecutorService()).get();
      }
      catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
      return true;
    }, factory.getFindMethodOptions());
    double silhouetteScore = new SilhouetteScore(session).getSilhouetteScoreResult();
    assertEquals(finalValue, silhouetteScore, "");
  }

  private @NotNull PsiMethod getMethod(String className, String methodName) {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    return ReadAction.computeBlocking(() -> {
      PsiClass anInterface = fixture.getJavaFacade().findClass(className, GlobalSearchScope.allScope(fixture.getProject()));
      return anInterface.findMethodsByName(methodName, false)[0];
    });
  }

  @Test
  public void testSilhouetteScore(){
    doTest("SilhouetteScoreValue", "test", 0.5);
  }
  @Test
  public void testSilhouetteScorePerfect(){
    doTest("SilhouetteScorePerfectValue", "test", 1);
  }
}
