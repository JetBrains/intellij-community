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
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.impl.source.tree.injected.ConcatenationInjectorManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.containers.ContainerUtil;
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

  private void startTest() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertNotNull(getFile().getText()); //to load text
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(getProject());

    Benchmark.newBenchmark(getTestName(false), this::doHighlighting)
      .setup(() -> PsiManager.getInstance(getProject()).dropPsiCaches())
      .start();
  }

  public void testAThinlet() {
    configureByFile(getFilePath(""));
    List<HighlightInfo> errors = highlightErrors();
    if (1165 != errors.size()) {
      doTest(getFilePath("_hl"), false, false);
      fail("Actual: " + errors.size());
    }
    startTest();
  }

  public void testAClassLoader() {
    configureByFile(getFilePath(""));
    List<HighlightInfo> errors = highlightErrors();
    if (90 != errors.size()) {
      doTest(getFilePath("_hl"), false, false);
      fail("Actual: " + errors.size());
    }
    startTest();
  }

  public void testDuplicateMethods() {
    int N = 1000;
    StringBuilder text = new StringBuilder(N * 100).append("class X {\n");
    for (int i = 0; i < N; i++) {
      text.append("public void visit(C").append(i).append(" param) {}\n");
    }
    for (int i = 0; i < N; i++) {
      text.append("class C").append(i).append(" {}\n");
    }
    text.append("}");
    configureFromFileText("x.java", text.toString());
    assertEmpty(highlightErrors());
    startTest();
  }

  public void testGetProjectPerformance() {
    configureByFile("/psi/resolve/ThinletBig.java");
    ProjectManager.getInstance().getDefaultProject();
    // wait for default project to dispose, otherwise it will be very slow
    // in the process, test that "the only open project" optimization is not broken
    while (ProjectManagerEx.getInstanceEx().isDefaultProjectInitialized()) {
      LOG.debug("waiting for default project dispose...");
      ((ProjectManagerImpl)ProjectManager.getInstance()).disposeDefaultProjectAndCleanupComponentsForDynamicPluginTests();
      UIUtil.dispatchAllInvocationEvents();
    }
    String message = ContainerUtil.map(ProjectManagerEx.getInstance().getOpenProjects(), p -> p + "; creation trace:"+((ProjectEx)p).getCreationTrace()+"\n").toString();
    assertNotNull(message, ProjectCoreUtil.theOnlyOpenProject());
    getFile().accept(new PsiRecursiveElementVisitor() {});
    Project myProject = getProject();
    Benchmark.newBenchmark("getProject() for nested elements", () -> {
      for (int k=0; k<5; k++) {
        getFile().accept(new PsiRecursiveElementVisitor() {
          int c;
          @Override
          public void visitElement(@NotNull PsiElement element) {
            for (int i = 0; i < 100; i++) {
              assertSame(myProject, element.getProject());
            }
            if (c++ % 100 == 0) {
              assertNotNull(ProjectCoreUtil.theOnlyOpenProject());
            }
            super.visitElement(element);
          }
        });
      }
    }).start();
  }
}