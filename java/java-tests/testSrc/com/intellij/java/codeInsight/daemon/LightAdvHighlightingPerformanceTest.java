// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.impl.source.tree.injected.ConcatenationInjectorManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SkipSlowTestLocally
@PerformanceUnitTest
@StressTestApplication
public class LightAdvHighlightingPerformanceTest {
  private static final Logger LOG = Logger.getInstance(LightAdvHighlightingPerformanceTest.class);
  // has to have awt
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk17()));
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();

    ExtensionsArea rootArea = ApplicationManager.getApplication().getExtensionArea();
    blockExtensionUntil(rootArea.getExtensionPoint(LanguageAnnotators.EP_NAME), myFixture.getTestRootDisposable());
    blockExtensionUntil(rootArea.getExtensionPoint(LineMarkerProviders.EP_NAME), myFixture.getTestRootDisposable());
    blockExtensionUntil(ConcatenationInjectorManager.EP_NAME.getPoint(myFixture.getProject()), myFixture.getTestRootDisposable());
    blockExtensionUntil(MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME.getPoint(myFixture.getProject()), myFixture.getTestRootDisposable());

    IntentionManager.getInstance().getAvailableIntentions();  // hack to avoid slowdowns in PyExtensionFactory
    JavaTestUtil.getJavaTestDataPath(); // to cache stuff
  }

  private static <T> void blockExtensionUntil(@NotNull ExtensionPoint<T> extensionPoint, @NotNull Disposable parent) {
    ((ExtensionPointImpl<T>)extensionPoint).maskAll(Collections.emptyList(), parent, false);
  }

  private String getFilePath(String suffix) {
    return LightAdvHighlightingTest.BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(true) + suffix + ".java";
  }

  private void startTest() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    Project project = daemon.getFixture().getProject();
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(project).commitAllDocuments());
    assertNotNull(ReadAction.computeBlocking(() -> daemon.getFixture().getFile().getText())); //to load text
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project);

    Benchmark.newBenchmark(daemon.getFixture().getTestName(false), daemon::doHighlighting)
      .setup(() -> EdtTestUtil.runInEdtAndWait(() -> PsiManager.getInstance(project).dropPsiCaches()))
      .start();
  }

  @Test
  public void testAThinlet() {
    daemonFixture.get().configureByFile(getFilePath(""));
    List<HighlightInfo> errors = daemonFixture.get().highlightErrors();
    if (1165 != errors.size()) {
      daemonFixture.get().doTest(getFilePath("_hl"), false, false);
      fail("Actual: " + errors.size());
    }
    startTest();
  }

  @Test
  public void testAClassLoader() {
    daemonFixture.get().configureByFile(getFilePath(""));
    List<HighlightInfo> errors = daemonFixture.get().highlightErrors();
    if (90 != errors.size()) {
      daemonFixture.get().doTest(getFilePath("_hl"), false, false);
      fail("Actual: " + errors.size());
    }
    startTest();
  }

  @Test
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
    daemonFixture.get().configureFromFileText("x.java", text.toString());
    List<HighlightInfo> errors = daemonFixture.get().highlightErrors();
    assertTrue(errors.isEmpty(), errors.toString());
    startTest();
  }

  @Test
  public void testGetProjectPerformance() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile("/psi/resolve/ThinletBig.java");
    EdtTestUtil.runInEdtAndWait(() -> {
      ProjectManager.getInstance().getDefaultProject();
      // wait for default project to dispose, otherwise it will be very slow
      // in the process, test that "the only open project" optimization is not broken
      while (ProjectManagerEx.getInstanceEx().isDefaultProjectInitialized()) {
        LOG.debug("waiting for default project dispose...");
        ((ProjectManagerImpl)ProjectManager.getInstance()).disposeDefaultProjectAndCleanupComponentsForDynamicPluginTests();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }
    });
    String message = ContainerUtil.map(ProjectManagerEx.getInstance().getOpenProjects(), p -> p + "; creation trace:"+((ProjectEx)p).getCreationTrace()+"\n").toString();
    assertNotNull(ProjectCoreUtil.theOnlyOpenProject(), message);
    ReadAction.runBlocking(() -> myFixture.getFile().accept(new PsiRecursiveElementVisitor() {}));
    Project myProject = myFixture.getProject();
    Benchmark.newBenchmark("daemonFixture.get().getFixture().getProject() for nested elements", () -> ReadAction.runBlocking(() -> {
      for (int k=0; k<5; k++) {
        myFixture.getFile().accept(new PsiRecursiveElementVisitor() {
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
    })).start();
  }
}