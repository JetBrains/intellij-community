// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.silhouette;

import com.intellij.JavaTestUtil;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import com.intellij.find.findUsages.similarity.SilhouetteScore;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;


public class SilhouetteScoreTest extends JavaPsiTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk18());
    createTestProjectStructure(getTestRoot());
  }

  private @NotNull String getTestRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/findSimilar/silhouetteScore/" + getTestName(true);
  }

  public void doTest(String className, String methodName, double finalValue){
    PsiMethod streamMethod = getMethod(className, methodName);
    final JavaFindUsagesHandlerFactory factory = JavaFindUsagesHandlerFactory.getInstance(getProject());
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
    assertEquals("", finalValue, silhouetteScore);
  }

  private @NotNull PsiMethod getMethod(String className, String methodName) {
    PsiClass anInterface = myJavaFacade.findClass(className, GlobalSearchScope.allScope(myProject));
    return anInterface.findMethodsByName(methodName, false)[0];
  }

  public void testSilhouetteScore(){
    doTest("SilhouetteScoreValue", "test", 0.5);
  }
  public void testSilhouetteScorePerfect(){
    doTest("SilhouetteScorePerfectValue", "test", 1);
  }
}
