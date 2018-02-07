// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager;
import com.intellij.codeInsight.hint.ParameterInfoController;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import com.intellij.util.ui.UIUtil;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

public abstract class AbstractParameterInfoTestCase extends LightFixtureCompletionTestCase {
  private EditorHintFixture myHintFixture;
  private int myStoredAutoPopupDelay;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myHintFixture = new EditorHintFixture(getTestRootDisposable());
    myStoredAutoPopupDelay = CodeInsightSettings.getInstance().PARAMETER_INFO_DELAY;
    CodeInsightSettings.getInstance().PARAMETER_INFO_DELAY = 100; // speed up tests
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().PARAMETER_INFO_DELAY = myStoredAutoPopupDelay;
    }
    finally {
      super.tearDown();
    }
  }

  public void configureJava(String text) {
    myFixture.configureByText(JavaFileType.INSTANCE, text);
  }

  public void showParameterInfo() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO);
    UIUtil.dispatchAllInvocationEvents();
  }

  public void checkHintContents(String hintText) {
    assertEquals(hintText, myHintFixture.getCurrentHintText());
  }

  public void type(String text) {
    myFixture.type(text);
  }

  private void waitForParameterInfoUpdate() throws TimeoutException {
    ParameterInfoController.waitForDelayedActions(getEditor(), 1, TimeUnit.MINUTES);
  }

  public static void waitTillAnimationCompletes(Editor editor) {
    long deadline = System.currentTimeMillis() + 60_000;
    while (ParameterHintsPresentationManager.getInstance().isAnimationInProgress(editor)) {
      if (System.currentTimeMillis() > deadline) fail("Too long waiting for animation to finish");
      LockSupport.parkNanos(10_000_000);
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  private void waitForAutoPopup() throws TimeoutException {
    AutoPopupController.getInstance(getProject()).waitForDelayedActions(1, TimeUnit.MINUTES);
  }

  public void waitForAllAsyncStuff() throws TimeoutException {
    waitForParameterInfoUpdate();
    myFixture.doHighlighting();
    waitTillAnimationCompletes(getEditor());
    waitForAutoPopup();
  }
}
