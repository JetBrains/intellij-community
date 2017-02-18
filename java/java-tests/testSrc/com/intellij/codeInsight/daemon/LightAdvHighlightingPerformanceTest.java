/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.tree.injected.JavaConcatenationInjectorManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;

import java.util.List;

@SkipSlowTestLocally
public class LightAdvHighlightingPerformanceTest extends LightDaemonAnalyzerTestCase {
  private final Disposable my = Disposer.newDisposable();
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    Disposer.register(my, BlockExtensions.create(Extensions.getRootArea().getExtensionPoint(LanguageAnnotators.EP_NAME)));
    Disposer.register(my, BlockExtensions.create(Extensions.getRootArea().getExtensionPoint(LineMarkerProviders.EP_NAME)));
    Disposer.register(my, BlockExtensions.create(Extensions.getArea(getProject()).getExtensionPoint(JavaConcatenationInjectorManager.CONCATENATION_INJECTOR_EP_NAME)));
    Disposer.register(my, BlockExtensions.create(Extensions.getArea(getProject()).getExtensionPoint(MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME)));

    IntentionManager.getInstance().getAvailableIntentionActions();  // hack to avoid slowdowns in PyExtensionFactory
    PathManagerEx.getTestDataPath(); // to cache stuff
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17(); // has to have awt
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(my);
    super.tearDown();
  }

  private static class BlockExtensions<T> implements Disposable {
    private final ExtensionPoint<T> myEp;
    private T[] myExtensions;
    public BlockExtensions(ExtensionPoint<T> extensionPoint) {
      myEp = extensionPoint;
      block();
    }
    void block() {
      myExtensions = myEp.getExtensions();
      for (T extension : myExtensions) {
        myEp.unregisterExtension(extension);
      }
    }

    void unblock() {
      for (T extension : myExtensions) {
        myEp.registerExtension(extension);
      }
      myExtensions = null;
    }
    @Override
    public void dispose() {
      unblock();
    }
    public static <T> BlockExtensions<T> create(ExtensionPoint<T> extensionPoint) {
      return new BlockExtensions<>(extensionPoint);
    }
  }

  private String getFilePath(final String suffix) {
    return LightAdvHighlightingTest.BASE_PATH + "/" + getTestName(true) + suffix + ".java";
  }

  private List<HighlightInfo> doTest(final int maxMillis) throws Exception {
    configureByFile(getFilePath(""));

    return startTest(maxMillis);
  }

  private List<HighlightInfo> startTest(int maxMillis) {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    getFile().getText(); //to load text
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(getProject());

    PlatformTestUtil.startPerformanceTest(getTestName(false), maxMillis, () -> {
      DaemonCodeAnalyzer.getInstance(getProject()).restart();
      doHighlighting();
    }).cpuBound().usesAllCPUCores().useLegacyScaling().assertTiming();

    return highlightErrors();
  }

  public void testAThinlet() throws Exception {
    List<HighlightInfo> errors = doTest(Math.max(10000, 24000 - JobSchedulerImpl.CORES_COUNT * 1000));
    if (1170 != errors.size()) {
      doTest(getFilePath("_hl"), false, false);
      fail("Actual: " + errors.size());
    }
  }

  public void testAClassLoader() throws Exception {
    List<HighlightInfo> errors = doTest(Math.max(1000, 10000 - JobSchedulerImpl.CORES_COUNT * 1000));
    if (171 != errors.size()) {
      doTest(getFilePath("_hl"), false, false);
      fail("Actual: " + errors.size());
    }
  }

  public void testDuplicateMethods() throws Exception {
    StringBuilder text = new StringBuilder("class X {\n");
    int N = 1000;
    for (int i=0;i<N;i++) {
      text.append("public void visit(C" + i + " param) {}\n");
    }
    for (int i=0;i<N;i++) {
      text.append("class C" + i + " {}\n");
    }
    text.append("}");
    configureFromFileText("x.java", text.toString());

    List<HighlightInfo> infos = startTest(Math.max(1000, 10000 - JobSchedulerImpl.CORES_COUNT * 1000));
    assertEmpty(infos);
  }
}
