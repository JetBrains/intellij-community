// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.dependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.packageDependencies.ui.DependenciesUsagesPanel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IJPL-257836: the usage search of the Dependency Viewer must not block a write action until the whole search ends.
 */
public class DependenciesUsagesPanelTest extends LightJavaCodeInsightFixtureTestCase {
  private static final long SEARCH_HOLD_MS = 10_000;

  public void testUsageSearchDoesNotBlockWriteAction() {
    PsiFile a = myFixture.addFileToProject("com/a/A.java", """
      package com.a;
      public class A {
        public void aa() {}
      }
      """);
    PsiFile b = myFixture.addFileToProject("com/b/B.java", """
      package com.b;
      import com.a.A;
      public class B {
        A myA = new A();
        public void bb() {
          myA.aa();
        }
      }
      """);

    AtomicBoolean holdSearch = new AtomicBoolean();
    CountDownLatch searchStarted = new CountDownLatch(1);
    DependenciesBuilder builder = new ForwardDependenciesBuilder(getProject(), new AnalysisScope(getProject())) {
      @Override
      public @NotNull Map<PsiFile, Set<PsiFile>> getDependencies() {
        if (holdSearch.get()) {
          // Keep the panel's search inside its read action, as a long search over a large scope does.
          searchStarted.countDown();
          long deadline = System.currentTimeMillis() + SEARCH_HOLD_MS;
          while (holdSearch.get() && System.currentTimeMillis() < deadline) {
            ProgressManager.checkCanceled();
            TimeoutUtil.sleep(5);
          }
        }
        return super.getDependencies();
      }
    };
    builder.analyze();

    AtomicReference<UsageInfo[]> shownUsages = new AtomicReference<>();
    DependenciesUsagesPanel panel = new DependenciesUsagesPanel(getProject(), new ArrayList<>(List.of(builder))) {
      @Override
      protected void showUsages(PsiElement @NotNull [] primaryElements, UsageInfo @NotNull [] usageInfos) {
        shownUsages.set(usageInfos);
      }
    };
    Disposer.register(getTestRootDisposable(), panel);

    holdSearch.set(true);
    panel.findUsages(Set.of(b), Set.of(a));
    PlatformTestUtil.waitWithEventsDispatching("the usage search did not start", () -> searchStarted.getCount() == 0, 10);

    long start = System.nanoTime();
    WriteAction.run(() -> { });
    long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    holdSearch.set(false);

    PlatformTestUtil.waitWithEventsDispatching("the usages were not shown", () -> shownUsages.get() != null, 30);
    assertTrue("the write action waited " + waitedMs + " ms for the usage search", waitedMs < SEARCH_HOLD_MS / 2);
    assertSize(4, shownUsages.get());
  }
}
