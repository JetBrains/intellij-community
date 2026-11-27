// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.find.groupSimilar;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.ExecutionException;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static com.intellij.testFramework.EqualsToFile.assertEqualsToFile;

@TestApplication
public class JavaFindUsagesGroupsTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    PsiTestUtil.removeAllRoots(fixture.getModule(), IdeaTestUtil.getMockJdk18());
    fixture.createTestProjectStructure(getTestRoot());
  }

  private @NotNull String getTestRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/findSimilar/usageGroups/" + heavyFixture.get().getTestName(true);
  }

  private void doTest(@NotNull PsiMethod streamMethod) {
    final JavaFindUsagesHandlerFactory factory = JavaFindUsagesHandlerFactory.getInstance(heavyFixture.get().getProject());
    JavaFindUsagesHandler handler = new JavaFindUsagesHandler(streamMethod, factory);
    ClusteringSearchSession session = new ClusteringSearchSession();
    handler.processElementUsages(streamMethod, info -> {
      try {
        ReadAction.nonBlocking(() -> {
          Usage usage = UsageInfoToUsageConverter.convertToSimilarUsage(new PsiMethod[]{streamMethod}, info, session);
          ((UsageInfo2UsageAdapter)usage).updateCachedPresentation();
        }).submit(AppExecutorUtil.getAppExecutorService()).get();
      }
      catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
      return true;
    }, factory.getFindMethodOptions());
    File file = new File(getTestRoot() + "/results.txt");
    assertEqualsToFile("", file, session.getClusters().toString());
  }

  private @NotNull PsiMethod getMethod(String className, String methodName) {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    return ReadAction.computeBlocking(() -> {
      PsiClass anInterface = fixture.getJavaFacade().findClass(className, GlobalSearchScope.allScope(fixture.getProject()));
      return anInterface.findMethodsByName(methodName, false)[0];
    });
  }

  @Test
  public void testStreamUsages() {
    doTest(getMethod("java.util.Collection", "stream"));
  }

  @Test
  public void testBinaryExpressions() {
    doTest(getMethod("Foo", "f"));
  }

  @Test
  public void testMapUsages() {
    try {
      Registry.get("similarity.find.usages.parent.statement.condition.feature").setValue(true);
      doTest(getMethod("java.util.Map", "put"));
    } finally {
      Registry.get("similarity.find.usages.fast.clustering").resetToDefault();
    }
  }

  @Test
  public void testCodeBlockFeatures() {
    doTest(getMethod("A", "foo"));
  }

  @Test
  public void testArrayAccess() { doTest(getMethod("Market", "getGoods")); }

  @Test
  public void testField() {
    try {
      Registry.get("similarity.find.usages.add.features.for.fields").setValue(true);
      doTest(getMethod("Test", "test"));
    }
    finally {
      Registry.get("similarity.find.usages.add.features.for.fields").resetToDefault();
    }
  }
}
