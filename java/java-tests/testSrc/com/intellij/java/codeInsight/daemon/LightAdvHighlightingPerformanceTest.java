// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.injected.ConcatenationInjectorManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@SkipSlowTestLocally
public class LightAdvHighlightingPerformanceTest extends LightDaemonAnalyzerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    Disposable disposable = getTestRootDisposable();
    Disposer.register(disposable, new BlockExtensions<>(Extensions.getRootArea().getExtensionPoint(LanguageAnnotators.EP_NAME)));
    Disposer.register(disposable, new BlockExtensions<>(Extensions.getRootArea().getExtensionPoint(LineMarkerProviders.EP_NAME)));
    Disposer.register(disposable, new BlockExtensions<>(ConcatenationInjectorManager.CONCATENATION_INJECTOR_EP_NAME.getPoint(getProject())));
    Disposer.register(disposable, new BlockExtensions<>(Extensions.getArea(getProject()).getExtensionPoint(MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME)));

    IntentionManager.getInstance().getAvailableIntentionActions();  // hack to avoid slowdowns in PyExtensionFactory
    PathManagerEx.getTestDataPath(); // to cache stuff
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17(); // has to have awt
  }

  private static final class BlockExtensions<T> implements Disposable {
    private final ExtensionPointImpl<T> myEp;

    BlockExtensions(@NotNull ExtensionPoint<T> extensionPoint) {
      myEp = (ExtensionPointImpl<T>)extensionPoint;
      myEp.maskAll(Collections.emptyList(), this);
    }

    @Override
    public void dispose() {
    }
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

    PlatformTestUtil.startPerformanceTest(getTestName(false), maxMillis, () -> doHighlighting())
      .setup(() -> PsiManager.getInstance(getProject()).dropPsiCaches())
      .attempts(10)
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
    StringBuilder text = new StringBuilder("class X {\n");
    for (int i = 0; i < N; i++) text.append("public void visit(C").append(i).append(" param) {}\n");
    for (int i = 0; i < N; i++) text.append("class C").append(i).append(" {}\n");
    text.append("}");
    configureFromFileText("x.java", text.toString());

    List<HighlightInfo> infos = startTest(3_300);
    assertEmpty(infos);
  }
}