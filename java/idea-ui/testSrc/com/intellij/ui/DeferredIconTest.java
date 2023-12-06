// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import kotlinx.coroutines.future.FutureKt;

import javax.swing.*;

public class DeferredIconTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected boolean isIconRequired() {
    return true;
  }

  public void test_icons_before_and_after_computation_are_equal() {
    PsiFile file = myFixture.addFileToProject("a.java", "class C {}");
    DeferredIconImpl<?> icon1 = (DeferredIconImpl<?>)file.getIcon(0);

    assertFalse(icon1.isDone());
    ensureEvaluated(icon1);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      file.getViewProvider().getDocument().insertString(0, " ");
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });

    DeferredIconImpl<?> icon2 = (DeferredIconImpl<?>)file.getIcon(0);
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