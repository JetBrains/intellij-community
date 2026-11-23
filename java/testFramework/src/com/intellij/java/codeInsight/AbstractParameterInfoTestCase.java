// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.testFramework.AutoPopupParameterInfoTestUtil;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link ParameterInfoFixtureKt#parameterInfoFixture} in a JUnit 5 test.
 */
@Deprecated
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
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void configureJava(String text) {
    myFixture.configureByText(JavaFileType.INSTANCE, text);
  }

  protected void showParameterInfo() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO);
    waitForParameterInfo();
  }

  public static void waitForParameterInfo() {
    ParameterInfoFixture.waitForParameterInfo();
  }

  protected void checkHintContents(String hintText) {
    String hint = myHintFixture.getCurrentHintText();
    // Remove any coloring and formatting
    if (hint != null) {
      hint = hint
        .replaceAll("<style>[^<]*</style>\\s*", "")
        .replaceAll("</?span[^>]*>", "");
    }
    assertEquals(hintText, hint);
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
    final LookupElement element = findLookupElementContainingText(elements, partOfItemText);
    selectItem(element);
  }

  protected static @NotNull LookupElement findLookupElementContainingText(final LookupElement @NotNull [] lookupItems, @NotNull final String lookupString) {
    return ParameterInfoFixture.findLookupElementContainingText(lookupItems, lookupString);
  }

  public static void waitTillAnimationCompletes(Editor editor) {
    ParameterInfoFixture.waitTillAnimationCompletes(editor);
  }

  protected void waitForAllAsyncStuff() {
    AutoPopupParameterInfoTestUtil.waitForParameterInfoUpdate(myFixture.getEditor());
    waitForParameterInfo();
    myFixture.doHighlighting();
    waitTillAnimationCompletes(getEditor());
    AutoPopupParameterInfoTestUtil.waitForAutoPopup(myFixture.getProject());
    waitForParameterInfo();
  }
}
