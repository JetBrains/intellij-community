// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager;
import com.intellij.codeInsight.hint.ParameterInfoController;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import com.intellij.util.ui.UIUtil;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

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
    CodeInsightSettings.getInstance().PARAMETER_INFO_DELAY = myStoredAutoPopupDelay;
    super.tearDown();
  }

  protected void configureJava(String text) {
    myFixture.configureByText(JavaFileType.INSTANCE, text);
  }

  protected void showParameterInfo() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO);
    waitForParameterInfo();
  }

  public static void waitForParameterInfo() {
    // effective there is a chain of 3 nonBlockingRead actions
    for (int i = 0; i < 3; i++) {
      UIUtil.dispatchAllInvocationEvents();
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    }
  }

  protected void checkHintContents(String hintText) {
    assertEquals(hintText, myHintFixture.getCurrentHintText());
  }

  public void checkResult(String text) {
    myFixture.checkResult(text);
  }

  public void complete(String partOfItemText) {
    LookupElement[] elements = myFixture.completeBasic();
    selectItem(elements, partOfItemText);
    waitForParameterInfo();
  }

  public void completeSmart() {
    myFixture.complete(CompletionType.SMART);
  }

  public void completeSmart(String partOfItemText) {
    LookupElement[] lookupElements = myFixture.complete(CompletionType.SMART);
    selectItem(lookupElements, partOfItemText);
    waitForParameterInfo();
  }

  private void selectItem(LookupElement[] elements, String partOfItemText) {
    LookupElement element = Stream.of(elements).filter(e -> {
      LookupElementPresentation p = new LookupElementPresentation();
      e.renderElement(p);
      return (p.getItemText() + p.getTailText()).contains(partOfItemText);
    }).findAny().orElseThrow(NoSuchElementException::new);
    selectItem(element);
  }

  private void waitForParameterInfoUpdate() throws TimeoutException {
    ParameterInfoController.waitForDelayedActions(getEditor(), 1, TimeUnit.MINUTES);
    waitForParameterInfo();
  }

  public static void waitTillAnimationCompletes(Editor editor) {
    long deadline = System.currentTimeMillis() + 60_000;
    while (ParameterHintsPresentationManager.getInstance().isAnimationInProgress(editor)) {
      if (System.currentTimeMillis() > deadline) fail("Too long waiting for animation to finish");
      LockSupport.parkNanos(10_000_000);
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  protected void waitForAutoPopup() {
    try {
      AutoPopupController.getInstance(getProject()).waitForDelayedActions(1, TimeUnit.MINUTES);
    }
    catch (TimeoutException e) {
      fail("Timed out waiting for auto-popup");
    }
  }

  protected void waitForAllAsyncStuff() {
    try { waitForParameterInfoUpdate(); } catch (TimeoutException e) { fail("Timed out waiting for parameter info update"); }
    myFixture.doHighlighting();
    waitTillAnimationCompletes(getEditor());
    waitForAutoPopup();
    waitForParameterInfo();
  }
}
