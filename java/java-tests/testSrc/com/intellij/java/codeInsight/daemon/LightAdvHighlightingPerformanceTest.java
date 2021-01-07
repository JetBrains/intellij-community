// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.impl.source.tree.injected.ConcatenationInjectorManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@SkipSlowTestLocally
public class LightAdvHighlightingPerformanceTest extends LightDaemonAnalyzerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    ExtensionsArea rootArea = ApplicationManager.getApplication().getExtensionArea();
    blockExtensionUntil(rootArea.getExtensionPoint(LanguageAnnotators.EP_NAME), getTestRootDisposable());
    blockExtensionUntil(rootArea.getExtensionPoint(LineMarkerProviders.EP_NAME), getTestRootDisposable());
    blockExtensionUntil(ConcatenationInjectorManager.EP_NAME.getPoint(getProject()), getTestRootDisposable());
    blockExtensionUntil(MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME.getPoint(getProject()), getTestRootDisposable());

    IntentionManager.getInstance().getAvailableIntentions();  // hack to avoid slowdowns in PyExtensionFactory
    PathManagerEx.getTestDataPath(); // to cache stuff
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17(); // has to have awt
  }

  private static <T> void blockExtensionUntil(@NotNull ExtensionPoint<T> extensionPoint, @NotNull Disposable parent) {
    ((ExtensionPointImpl<T>)extensionPoint).maskAll(Collections.emptyList(), parent, false);
  }

  private String getFilePath(String suffix) {
    return LightAdvHighlightingTest.BASE_PATH + "/" + getTestName(true) + suffix + ".java";
  }

  private List<HighlightInfo> doTest(int maxMillis) {
    configureByFile(getFilePath(""));
    return startTest(maxMillis);
  }

  private List<HighlightInfo> startTest(int maxMillis) {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertNotNull(getFile().getText()); //to load text
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(getProject());

    PlatformTestUtil.startPerformanceTest(getTestName(false), maxMillis, this::doHighlighting)
      .setup(() -> PsiManager.getInstance(getProject()).dropPsiCaches())
      .usesAllCPUCores().assertTiming();

    return highlightErrors();
  }

  public void testAThinlet() {
    List<HighlightInfo> errors = doTest(8_000);
    if (1170 != errors.size()) {
      doTest(getFilePath("_hl"), false, false);
      fail("Actual: " + errors.size());
    }
  }

  public void testAClassLoader() {
    List<HighlightInfo> errors = doTest(800);
    if (92 != errors.size()) {
      doTest(getFilePath("_hl"), false, false);
      fail("Actual: " + errors.size());
    }
  }

  public void testDuplicateMethods() {
    int N = 1000;
    StringBuilder text = new StringBuilder(N * 100).append("class X {\n");
    for (int i = 0; i < N; i++) text.append("public void visit(C").append(i).append(" param) {}\n");
    for (int i = 0; i < N; i++) text.append("class C").append(i).append(" {}\n");
    text.append("}");
    configureFromFileText("x.java", text.toString());

    List<HighlightInfo> infos = startTest(3_300);
    assertEmpty(infos);
  }

  public void testGetProjectPerformance() {
    configureByFile("/psi/resolve/ThinletBig.java");
    // wait for default project to dispose, otherwise it will be very slow
    while (ProjectManagerEx.getInstanceEx().isDefaultProjectInitialized()) {
      UIUtil.dispatchAllInvocationEvents();
      if (System.currentTimeMillis() % 10_000 < 100) {
        System.out.println("waiting for default project dispose...");
        TimeoutUtil.sleep(100);
      }
    }
    assertNotNull(ProjectCoreUtil.theOnlyOpenProject());
    getFile().accept(new PsiRecursiveElementVisitor() {});
    Project myProject = getProject();
    PlatformTestUtil.startPerformanceTest("getProject() for nested elements", 300, () -> {
      getFile().accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          for (int i = 0; i < 10; i++) {
            assertSame(myProject, element.getProject());
          }
          super.visitElement(element);
        }
      });
    }).assertTiming();
  }
}