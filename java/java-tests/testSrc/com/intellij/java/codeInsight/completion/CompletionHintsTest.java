/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager;
import com.intellij.codeInsight.hint.ParameterInfoController;
import com.intellij.codeInsight.hints.JavaInlayParameterHintsProvider;
import com.intellij.codeInsight.hints.Option;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.util.ui.UIUtil;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

public class CompletionHintsTest extends LightFixtureCompletionTestCase {
  private RegistryValue myRegistryValue = Registry.get("java.completion.argument.hints");
  private boolean myStoredRegistryValue;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myStoredRegistryValue = myRegistryValue.asBoolean();
    myRegistryValue.setValue(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myRegistryValue.setValue(myStoredRegistryValue);
    }
    finally {
      super.tearDown();
    }
  }

  public void testBasicScenarioWithHintsDisabledForMethod() throws Exception {
    // check hints appearance on completion
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    myFixture.checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>, <hint text=\"value:\"/>) } }");

    // check that hints don't disappear after daemon highlighting passes
    waitForAllAsyncStuff();
    myFixture.checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>, <hint text=\"value:\"/>) } }");

    // test Tab/Shift+Tab navigation
    myFixture.checkResult("class C { void m() { System.setProperty(<caret>, ) } }");
    assertTrue(myFixture.getEditor().getCaretModel().getLogicalPosition().leansForward);
    next();
    myFixture.checkResult("class C { void m() { System.setProperty(, <caret>) } }");
    assertTrue(myFixture.getEditor().getCaretModel().getLogicalPosition().leansForward);
    prev();
    myFixture.checkResult("class C { void m() { System.setProperty(<caret>, ) } }");
    assertTrue(myFixture.getEditor().getCaretModel().getLogicalPosition().leansForward);

    // test hints remain shown while entering parameter values
    myFixture.type("\"a");
    next();
    myFixture.type("\"b");
    waitForAllAsyncStuff();
    myFixture.checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>\"a\", <hint text=\"value:\"/>\"b\") } }");

    // test hints disappearance when caret moves out of parameter list
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);

    waitForAllAsyncStuff();
    myFixture.checkResultWithInlays("class C { void m() { System.setProperty(\"a\", \"b\") } }");
  }

  public void testBasicScenarioWithHintsEnabledForMethod() throws Exception {
    // check hints appearance on completion
    configureJava("class C { void m() { Character.for<caret> } }");
    complete("forDigit");
    myFixture.checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>, <hint text=\"radix:\"/>) } }");

    // check that hints don't disappear after daemon highlighting passes
    waitForAllAsyncStuff();
    myFixture.checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>, <hint text=\"radix:\"/>) } }");

    // test Tab/Shift+Tab navigation
    myFixture.checkResult("class C { void m() { Character.forDigit(<caret>, ) } }");
    assertTrue(myFixture.getEditor().getCaretModel().getLogicalPosition().leansForward);
    next();
    myFixture.checkResult("class C { void m() { Character.forDigit(, <caret>) } }");
    assertTrue(myFixture.getEditor().getCaretModel().getLogicalPosition().leansForward);
    prev();
    myFixture.checkResult("class C { void m() { Character.forDigit(<caret>, ) } }");
    assertTrue(myFixture.getEditor().getCaretModel().getLogicalPosition().leansForward);

    // test hints remain shown while entering parameter values
    myFixture.type("1");
    next();
    myFixture.type("2");
    waitForAllAsyncStuff();
    myFixture.checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>1, <hint text=\"radix:\"/>2) } }");

    // test hints don't disappear when caret moves out of parameter list
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);

    waitForAllAsyncStuff();
    myFixture.checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>1, <hint text=\"radix:\"/>2) } }");
  }

  public void testWithHintsEnabledForNonLiterals() throws Exception {
    Option option = JavaInlayParameterHintsProvider.Companion.getInstance().isShowForParamsWithSameType();
    boolean savedValue = option.get();
    try {
      option.set(true);

      configureJava("class C { void m() { Character.for<caret> } }");
      complete("forDigit");
      myFixture.checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>, <hint text=\"radix:\"/>) } }");

      waitForAllAsyncStuff();
      myFixture.checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>, <hint text=\"radix:\"/>) } }");
    }
    finally {
      option.set(savedValue);
    }
  }

  public void testSwitchingOverloads() {
    configureJava("class C { void m() { Character.to<caret> } }");
    complete("toChars(int codePoint)");
    myFixture.checkResultWithInlays("class C { void m() { Character.toChars(<hint text=\"codePoint:\"/>) } }");
    showParameterInfo();
    myFixture.performEditorAction("MethodOverloadSwitchDown");
    myFixture.checkResultWithInlays("class C { void m() { Character.toChars(<hint text=\"codePoint:\"/>, <hint text=\"dst:\"/>, <hint text=\"dstIndex:\"/>) } }");
  }

  public void testSwitchingOverloadsWithParameterFilled() {
    configureJava("class C { void m() { Character.to<caret> } }");
    complete("toChars(int codePoint)");
    type("123");
    myFixture.checkResultWithInlays("class C { void m() { Character.toChars(<hint text=\"codePoint:\"/>123) } }");
    showParameterInfo();
    myFixture.performEditorAction("MethodOverloadSwitchDown");
    myFixture.checkResultWithInlays("class C { void m() { Character.toChars(<hint text=\"codePoint:\"/>123, <hint text=\"dst:\"/>, <hint text=\"dstIndex:\"/>) } }");
    myFixture.checkResult("class C { void m() { Character.toChars(123, <caret>, ) } }");
  }

  public void testNoHintsForMethodReference() {
    configureJava("class C {\n" +
                                                     "  interface I { void i(int p); }\n" +
                                                     "  void referenced(int a) {}\n" +
                                                     "  void m(I lambda) {}\n" +
                                                     "  void m2() { m(this::<caret>) }\n" +
                                                     "}");
    complete("referenced");
    myFixture.checkResultWithInlays("class C {\n" +
                                    "  interface I { void i(int p); }\n" +
                                    "  void referenced(int a) {}\n" +
                                    "  void m(I lambda) {}\n" +
                                    "  void m2() { m(this::referenced) }\n" +
                                    "}");
  }

  public void testNestedCompletion() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    myFixture.checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>, <hint text=\"value:\"/>) } }");
    myFixture.type("System.getPro");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    myFixture.checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>), <hint text=\"value:\"/>) } }");
    myFixture.checkResult("class C { void m() { System.setProperty(System.getProperty(<caret>, ), ) } }");
  }

  public void testTabWithNestedCompletion() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    myFixture.type("System.getPro");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    myFixture.checkResult("class C { void m() { System.setProperty(System.getProperty(<caret>, ), ) } }");
    next();
    myFixture.checkResult("class C { void m() { System.setProperty(System.getProperty(, <caret>), ) } }");
    next();
    myFixture.checkResult("class C { void m() { System.setProperty(System.getProperty(, )<caret>, ) } }");
    next();
    myFixture.checkResult("class C { void m() { System.setProperty(System.getProperty(, ), <caret>) } }");
    next();
    myFixture.checkResult("class C { void m() { System.setProperty(System.getProperty(, ), )<caret> } }");
  }

  public void testNoHintsForMethodWithOneParameterFromBlackList() {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key)");
    myFixture.checkResultWithInlays("class C { void m() { System.getProperty() } }");
  }

  public void testHintsDisappearWhenNumberOfParametersIsChangedDirectly() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    myFixture.checkResultWithInlays("class C { void m() { System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>) } }");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE);
    waitForAllAsyncStuff();
    myFixture.checkResultWithInlays("class C { void m() { System.getProperty( ) } }");
  }

  public void testHintsDisappearWhenNumberOfParametersIsChangedDirectlyWithNoOverloads() throws Exception {
    configureJava("class C { void m() { Character.for<caret> } }");
    complete("forDigit");
    myFixture.checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>, <hint text=\"radix:\"/>) } }");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE);
    waitForAllAsyncStuff();
    myFixture.checkResultWithInlays("class C { void m() { Character.forDigit( ) } }");
  }

  public void testCaretIsToTheRightOfHintAfterSmartInnerCompletion() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    type("new String().trim");
    myFixture.complete(CompletionType.SMART);
    myFixture.checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>new String().trim(), <hint text=\"value:\"/>) } }");
    myFixture.checkResult("class C { void m() { System.setProperty(new String().trim(), <caret>) } }");
    assertEquals(getEditor().offsetToVisualPosition(getEditor().getCaretModel().getOffset(), true, false), 
                 getEditor().getCaretModel().getVisualPosition());
  }

  public void testNoHintsDuplicationWhenTypingToTheLeftOfHint() throws Exception {
    configureJava("class C { void m() { Character.for<caret> } }");
    complete("forDigit");
    waitForAllAsyncStuff();
    type("1");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
    type("2");
    waitForAllAsyncStuff();
    myFixture.checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>1,2 <hint text=\"radix:\"/>) } }");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
    waitForAllAsyncStuff();
    myFixture.checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>1,<hint text=\"radix:\"/>2 ) } }");
  }

  public void testIntroduceVariableIntention() throws Exception {
    configureJava("class C {\n" +
                                                     "    void m() {\n" +
                                                     "        Character.for<caret>\n" +
                                                     "    }\n" +
                                                     "}");
    complete("forDigit");
    waitForAllAsyncStuff();
    IntentionAction intention = myFixture.findSingleIntention("Introduce local variable");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("class C {\n" +
                          "    void m() {\n" +
                          "        int i = ;\n" +
                          "        Character.forDigit(i<caret>, )\n" +
                          "    }\n" +
                          "}");
  }

  public void testIntroduceVariableIntentionInIfWithoutBraces() throws Exception {
    configureJava("class C {\n" +
                                                     "    void m() {\n" +
                                                     "        if (true) Character.for<caret>\n" +
                                                     "    }\n" +
                                                     "}");
    complete("forDigit");
    waitForAllAsyncStuff();
    IntentionAction intention = myFixture.findSingleIntention("Introduce local variable");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult("class C {\n" +
                          "    void m() {\n" +
                          "        if (true) {\n" +
                          "            int i = ;\n" +
                          "            Character.forDigit(i<caret>, )\n" +
                          "        }\n" +
                          "    }\n" +
                          "}");
  }

  public void testPrevParameterFromOutside() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    next();
    next();
    prev();
    myFixture.checkResult("class C { void m() { System.getProperty(, <caret>) } }");
    myFixture.checkResultWithInlays("class C { void m() { System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>) } }");
    assertFalse(getEditor().getInlayModel().hasInlineElementAt(getEditor().getCaretModel().getVisualPosition()));
  }

  private void prev() {
    myFixture.performEditorAction("PrevParameter");
  }

  private void next() {
    myFixture.performEditorAction("NextParameter");
  }

  private void configureJava(String text) {
    myFixture.configureByText(JavaFileType.INSTANCE, text);
  }

  private void waitForParameterInfoUpdate() throws TimeoutException {
    ParameterInfoController.waitForDelayedActions(getEditor(), 1, TimeUnit.MINUTES);
  }

  private void showParameterInfo() {
    myFixture.performEditorAction("ParameterInfo");
    UIUtil.dispatchAllInvocationEvents();
  }

  private void complete(String partOfItemText) {
    LookupElement[] elements = myFixture.completeBasic();
    LookupElement element = Stream.of(elements).filter(e -> {
      LookupElementPresentation p = new LookupElementPresentation();
      e.renderElement(p);
      return (p.getItemText() + p.getTailText()).contains(partOfItemText);
    }).findAny().get();
    selectItem(element);
  }

  private void waitTillAnimationCompletes() {
    long deadline = System.currentTimeMillis() + 60_000;
    while (ParameterHintsPresentationManager.getInstance().isAnimationInProgress(getEditor())) {
      if (System.currentTimeMillis() > deadline) fail("Too long waiting for animation to finish");
      LockSupport.parkNanos(10_000_000);
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  private void waitForAllAsyncStuff() throws TimeoutException {
    waitForParameterInfoUpdate();
    myFixture.doHighlighting();
    waitTillAnimationCompletes();
  }
}