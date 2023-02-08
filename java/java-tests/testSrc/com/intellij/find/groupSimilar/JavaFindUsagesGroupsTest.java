// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.groupSimilar;

import com.intellij.JavaTestUtil;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.testFramework.EqualsToFile.assertEqualsToFile;

public class JavaFindUsagesGroupsTest extends JavaPsiTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk18());
    createTestProjectStructure(getTestRoot());
  }

  private @NotNull String getTestRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/findSimilar/usageGroups/" + getTestName(true);
  }

  private void doTest(@NotNull PsiMethod streamMethod) {
    final JavaFindUsagesHandlerFactory factory = JavaFindUsagesHandlerFactory.getInstance(getProject());
    JavaFindUsagesHandler handler = new JavaFindUsagesHandler(streamMethod, factory);
    ClusteringSearchSession session = new ClusteringSearchSession();
    handler.processElementUsages(streamMethod, info -> {
      UsageInfoToUsageConverter.convertToSimilarUsage(new PsiMethod[]{streamMethod}, info, session);
      return true;
    }, factory.getFindMethodOptions());
    File file = new File(getTestRoot() + "/results.txt");
    assertEqualsToFile("", file, session.getClusters().toString());
  }

  private @NotNull PsiMethod getMethod(String className, String methodName) {
    PsiClass anInterface = myJavaFacade.findClass(className, GlobalSearchScope.allScope(myProject));
    return anInterface.findMethodsByName(methodName, false)[0];
  }

  public void testStreamUsages() {
    doTest(getMethod("java.util.Collection", "stream"));
  }

  public void testBinaryExpressions() {
    doTest(getMethod("Foo", "f"));
  }

  public void testMapUsages() {
    try {
      Registry.get("similarity.find.usages.parent.statement.condition.feature").setValue(true);
      doTest(getMethod("java.util.Map", "put"));
    } finally {
      Registry.get("similarity.find.usages.fast.clustering").resetToDefault();
    }
  }

  public void testCodeBlockFeatures() {
    doTest(getMethod("A", "foo"));
  }

  public void testArrayAccess() { doTest(getMethod("Market", "getGoods")); }
}
