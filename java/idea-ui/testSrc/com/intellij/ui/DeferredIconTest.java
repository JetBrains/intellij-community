// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.icons.CoreIconManager;
import kotlinx.coroutines.future.FutureKt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("AT-4013")
@TestApplication
public class DeferredIconTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpIcons() {
    IconManager.Companion.activate(new CoreIconManager());
  }

  @AfterEach
  void tearDownIcons() {
    IconManager.Companion.deactivate();
    IconLoader.INSTANCE.clearCacheInTests();
  }

  @Test
  public void test_icons_before_and_after_computation_are_equal() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("a.java", "class C {}");
    DeferredIconImpl<?> icon1 = (DeferredIconImpl<?>)ReadAction.computeBlocking(() -> file.getIcon(0));

    assertFalse(icon1.isDone());
    ensureEvaluated(icon1);

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      file.getViewProvider().getDocument().insertString(0, " ");
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    });

    DeferredIconImpl<?> icon2 = (DeferredIconImpl<?>)ReadAction.computeBlocking(() -> file.getIcon(0));
    assertFalse(icon2.isDone());
    assertEquals(icon1, icon2);

    ensureEvaluated(icon2);
    assertEquals(icon1, icon2);
  }

  private static void ensureEvaluated(DeferredIconImpl<?> icon1) {
    PlatformTestUtil.waitForFuture(FutureKt.asCompletableFuture(icon1.scheduleEvaluation(new JLabel(), 0, 0)), 10_000);
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    assertTrue(icon1.isDone());
  }
}