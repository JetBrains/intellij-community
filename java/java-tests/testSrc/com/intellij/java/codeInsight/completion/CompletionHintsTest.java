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

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
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
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import com.intellij.util.ui.UIUtil;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

public class CompletionHintsTest extends LightFixtureCompletionTestCase {
  private boolean myStoredSettingValue;
  private EditorHintFixture myHintFixture;
  private int myStoredAutoPopupDelay;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myStoredSettingValue = CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION;
    CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = true;
    myHintFixture = new EditorHintFixture(getTestRootDisposable());
    myStoredAutoPopupDelay = CodeInsightSettings.getInstance().PARAMETER_INFO_DELAY;
    CodeInsightSettings.getInstance().PARAMETER_INFO_DELAY = 100; // speed up tests
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().PARAMETER_INFO_DELAY = myStoredAutoPopupDelay;
      CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = myStoredSettingValue;
    }
    finally {
      super.tearDown();
    }
  }

  public void testBasicScenarioWithHintsDisabledForMethod() throws Exception {
    // check hints appearance on completion
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <hint text=\"value:\"/>) } }");

    // check that hints don't disappear after daemon highlighting passes
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <hint text=\"value:\"/>) } }");

    // test Tab/Shift+Tab navigation
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>, <HINT text=\"value:\"/><caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <hint text=\"value:\"/>) } }");

    // test hints remain shown while entering parameter values
    myFixture.type("\"a");
    next();
    myFixture.type("\"b");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>\"a\", <HINT text=\"value:\"/>\"b<caret>\") } }");

    // test hints disappearance when caret moves out of parameter list
    right();
    right();
    right();
    right();

    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(\"a\", \"b\") }<caret> }");
  }

  public void testBasicScenarioWithHintsEnabledForMethod() throws Exception {
    // check hints appearance on completion
    configureJava("class C { void m() { Character.for<caret> } }");
    complete("forDigit");
    checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <hint text=\"radix:\"/>) } }");

    // check that hints don't disappear after daemon highlighting passes
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <hint text=\"radix:\"/>) } }");

    // test Tab/Shift+Tab navigation
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>, <HINT text=\"radix:\"/><caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <hint text=\"radix:\"/>) } }");

    // test hints remain shown while entering parameter values
    myFixture.type("1");
    next();
    myFixture.type("2");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>1, <HINT text=\"radix:\"/>2<caret>) } }");

    // test hints don't disappear when caret moves out of parameter list
    right();
    right();
    right();

    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>1, <hint text=\"radix:\"/>2) }<caret> }");
  }

  public void testWithHintsEnabledForNonLiterals() throws Exception {
    Option option = JavaInlayParameterHintsProvider.Companion.getInstance().isShowForParamsWithSameType();
    boolean savedValue = option.get();
    try {
      option.set(true);

      configureJava("class C { void m() { Character.for<caret> } }");
      complete("forDigit");
      checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <hint text=\"radix:\"/>) } }");

      waitForAllAsyncStuff();
      checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <hint text=\"radix:\"/>) } }");
    }
    finally {
      option.set(savedValue);
    }
  }

  public void testSwitchingOverloads() {
    configureJava("class C { void m() { Character.to<caret> } }");
    complete("toChars(int codePoint)");
    checkResultWithInlays("class C { void m() { Character.toChars(<HINT text=\"codePoint:\"/><caret>) } }");
    showParameterInfo();
    down();
    checkResultWithInlays(
      "class C { void m() { Character.toChars(<HINT text=\"codePoint:\"/><caret>, <hint text=\"dst:\"/>, <hint text=\"dstIndex:\"/>) } }");
  }

  public void testSwitchingOverloadsWithParameterFilled() {
    configureJava("class C { void m() { Character.to<caret> } }");
    complete("toChars(int codePoint)");
    type("123");
    checkResultWithInlays("class C { void m() { Character.toChars(<HINT text=\"codePoint:\"/>123<caret>) } }");
    showParameterInfo();
    down();
    checkResultWithInlays("class C { void m() { Character.toChars(<hint text=\"codePoint:\"/>123, <HINT text=\"dst:\"/><caret>, <hint text=\"dstIndex:\"/>) } }");
  }

  public void testSwitchingOverloadsForMethodWithDisabledHints() throws Exception {
    configureJava("class C {\n" +
                  "  int some(int from, int to) { return 0; }\n" +
                  "  int some(int from, int to, int other) { return 0; }\n" +
                  "  void m() { s<caret> }\n" +
                  "}");
    complete("some(int from, int to, int other)");
    checkResultWithInlays("class C {\n" +
                          "  int some(int from, int to) { return 0; }\n" +
                          "  int some(int from, int to, int other) { return 0; }\n" +
                          "  void m() { some(<HINT text=\"from:\"/><caret>, <hint text=\"to:\"/>, <hint text=\"other:\"/>) }\n" +
                          "}");
    showParameterInfo();
    down();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "  int some(int from, int to) { return 0; }\n" +
                          "  int some(int from, int to, int other) { return 0; }\n" +
                          "  void m() { some(<HINT text=\"from:\"/><caret>, <hint text=\"to:\"/>) }\n" +
                          "}");
  }

  public void testNoHintsForMethodReference() {
    configureJava("class C {\n" +
                  "  interface I { void i(int p); }\n" +
                  "  void referenced(int a) {}\n" +
                  "  void m(I lambda) {}\n" +
                  "  void m2() { m(this::<caret>) }\n" +
                  "}");
    complete("referenced");
    checkResultWithInlays("class C {\n" +
                          "  interface I { void i(int p); }\n" +
                          "  void referenced(int a) {}\n" +
                          "  void m(I lambda) {}\n" +
                          "  void m2() { m(this::referenced<caret>) }\n" +
                          "}");
  }

  public void testNestedCompletion() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <hint text=\"value:\"/>) } }");
    myFixture.type("System.getPro");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<HINT text=\"key:\"/><caret>, <hint text=\"def:\"/>), <hint text=\"value:\"/>) } }");
  }

  public void testTabWithNestedCompletion() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    myFixture.type("System.getPro");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<HINT text=\"key:\"/><caret>, <hint text=\"def:\"/>), <hint text=\"value:\"/>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<hint text=\"key:\"/>, <HINT text=\"def:\"/><caret>), <hint text=\"value:\"/>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/>System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>)<caret>, <hint text=\"value:\"/>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>), <HINT text=\"value:\"/><caret>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>), <hint text=\"value:\"/>)<caret> } }");
  }

  public void testNoHintsForMethodWithOneParameterFromBlackList() {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key)");
    checkResultWithInlays("class C { void m() { System.getProperty(<caret>) } }");
  }

  public void testHintsDisappearWhenNumberOfParametersIsChangedDirectly() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    checkResultWithInlays("class C { void m() { System.getProperty(<HINT text=\"key:\"/><caret>, <hint text=\"def:\"/>) } }");
    delete();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<caret> ) } }");
  }

  public void testHintsDisappearWhenNumberOfParametersIsChangedDirectlyWithNoOverloads() throws Exception {
    configureJava("class C { void m() { Character.for<caret> } }");
    complete("forDigit");
    checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <hint text=\"radix:\"/>) } }");
    delete();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<caret> ) } }");
  }

  public void testCaretIsToTheRightOfHintAfterSmartInnerCompletion() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    type("new String().trim");
    myFixture.complete(CompletionType.SMART);
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>new String().trim(), <HINT text=\"value:\"/><caret>) } }");
  }

  public void testNoHintsDuplicationWhenTypingToTheLeftOfHint() throws Exception {
    configureJava("class C { void m() { Character.for<caret> } }");
    complete("forDigit");
    waitForAllAsyncStuff();
    type("1");
    right();
    type("2");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>1,<HINT text=\"radix:\"/>2<caret> ) } }");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>1,<hint text=\"radix:\"/>2 ) } }<caret>");
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
    checkResult("class C {\n" +
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
    checkResult("class C {\n" +
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
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<hint text=\"key:\"/>, <HINT text=\"def:\"/><caret>) } }");
  }

  public void testPrevParameterFromOutsideWhenParametersAreNotEmpty() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    type("\"a");
    next();
    type("\"b");
    next();
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<hint text=\"key:\"/>\"a\", <HINT text=\"def:\"/><caret>\"b\") } }");
  }

  public void testVararg() throws Exception {
    configureJava("class C { void m() { String.for<caret> } }");
    complete();
    checkResultWithInlays("class C { void m() { String.format(<HINT text=\"format:\"/><caret><hint text=\", args:\"/>) } }");
    type("\"a");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<hint text=\"format:\"/>\"a\", <HINT text=\"args:\"/><caret>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<hint text=\"format:\"/>\"a\"<hint text=\", args:\"/>)<caret> } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<hint text=\"format:\"/>\"a\", <HINT text=\"args:\"/><caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<HINT text=\"format:\"/>\"a\"<caret><hint text=\", args:\"/>) } }");
  }

  public void testVarargWithNoMandatoryArguments() throws Exception {
    configureJava("class C { int vararg(int... args){ return 0; } void m() { varar<caret> } }");
    complete();
    checkResultWithInlays("class C { int vararg(int... args){ return 0; } void m() { vararg(<HINT text=\"args:\"/><caret>) } }");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int... args){ return 0; } void m() { vararg(<hint text=\"args:\"/>1, <caret>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int... args){ return 0; } void m() { vararg(<hint text=\"args:\"/>1)<caret> } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int... args){ return 0; } void m() { vararg(<hint text=\"args:\"/>1, <caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int... args){ return 0; } void m() { vararg(<HINT text=\"args:\"/>1<caret>) } }");
  }

  public void testVarargWithTwoMandatoryArguments() throws Exception {
    configureJava("class C { int vararg(int a, int b, int... args){ return 0; } void m() { varar<caret> } }");
    complete();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<HINT text=\"a:\"/><caret>, <hint text=\"b:\"/><hint text=\", args:\"/>) } }");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<hint text=\"a:\"/>1, <HINT text=\"b:\"/><caret><hint text=\", args:\"/>) } }");
    type("2");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<hint text=\"a:\"/>1, <hint text=\"b:\"/>2, <HINT text=\"args:\"/><caret>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<hint text=\"a:\"/>1, <hint text=\"b:\"/>2<hint text=\", args:\"/>)<caret> } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<hint text=\"a:\"/>1, <hint text=\"b:\"/>2, <HINT text=\"args:\"/><caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<hint text=\"a:\"/>1, <HINT text=\"b:\"/>2<caret><hint text=\", args:\"/>) } }");
  }

  public void testHintsDontDisappearWhenNavigatingAwayFromUncompletedInvocation() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <hint text=\"value:\"/>) } }");
    home();
    type(' ');
    waitForAllAsyncStuff();
    checkResultWithInlays(" <caret>class C { void m() { System.setProperty(<hint text=\"key:\"/>, <hint text=\"value:\"/>) } }");
  }

  public void testHintsDontDisappearOnUnfinishedInputForMethodWithOneParameter() throws Exception {
    configureJava("class C { void m() { System.clear<caret> } }");
    complete();
    checkResultWithInlays("class C { void m() { System.clearProperty(<HINT text=\"key:\"/><caret>) } }");
    home();
    waitForAllAsyncStuff();
    checkResultWithInlays("<caret>class C { void m() { System.clearProperty(<hint text=\"key:\"/>) } }");
  }

  public void testSeveralParametersCompletion() throws Exception {
    configureJava("class P {\n" +
                  "    void method(int a, int b) {}\n" +
                  "}\n" +
                  "class C extends P {\n" +
                  "    void method(int a, int b) {\n" +
                  "        super.meth<caret>\n" +
                  "    }\n" +
                  "}");
    complete();
    checkResultWithInlays("class P {\n" +
                          "    void method(int a, int b) {}\n" +
                          "}\n" +
                          "class C extends P {\n" +
                          "    void method(int a, int b) {\n" +
                          "        super.method(<HINT text=\"a:\"/><caret>, <hint text=\"b:\"/>);\n" +
                          "    }\n" +
                          "}");
    complete("a, b");
    waitForAllAsyncStuff();
    checkResultWithInlays("class P {\n" +
                          "    void method(int a, int b) {}\n" +
                          "}\n" +
                          "class C extends P {\n" +
                          "    void method(int a, int b) {\n" +
                          "        super.method(a, b);<caret>\n" +
                          "    }\n" +
                          "}");
  }

  public void testCompletionHintsAreShownEvenWhenStaticHintsAreDisabled() throws Exception {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    boolean oldValue = settings.isShowParameterNameHints();
    try {
      settings.setShowParameterNameHints(false);

      // check hints appearance on completion
      configureJava("class C { void m() { Character.for<caret> } }");
      complete("forDigit");
      checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <hint text=\"radix:\"/>) } }");

      // check that hints don't disappear after daemon highlighting passes
      waitForAllAsyncStuff();
      checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <hint text=\"radix:\"/>) } }");

      // test Tab/Shift+Tab navigation
      next();
      waitForAllAsyncStuff();
      checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>, <HINT text=\"radix:\"/><caret>) } }");
      prev();
      waitForAllAsyncStuff();
      checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <hint text=\"radix:\"/>) } }");

      // test hints remain shown while entering parameter values
      myFixture.type("1");
      next();
      myFixture.type("2");
      waitForAllAsyncStuff();
      checkResultWithInlays("class C { void m() { Character.forDigit(<hint text=\"digit:\"/>1, <HINT text=\"radix:\"/>2<caret>) } }");

      // test hints disappear when caret moves out of parameter list
      right();
      right();
      right();

      waitForAllAsyncStuff();
      checkResultWithInlays("class C { void m() { Character.forDigit(1, 2) }<caret> }");
    }
    finally {
      settings.setShowParameterNameHints(oldValue);
    }
  }
  
  public void testLargeNumberOfParameters() {
    configureJava("class C {\n" +
                  "    void mmm(int a, int b, int c, int d, int e, int f) {}\n" +
                  "    void m2() { mm<caret> }\n" +
                  "}");
    complete();
    checkResultWithInlays("class C {\n" +
                          "    void mmm(int a, int b, int c, int d, int e, int f) {}\n" +
                          "    void m2() { mmm(<HINT text=\"a:\"/><caret>, <hint text=\"b:\"/>, <hint text=\"c:\"/>, <hint text=\"d:\"/>, <hint text=\"e:\"/>, <hint text=\"f:\"/>); }\n" +
                          "}");
  }
  
  public void testNestedContextIsNotDisposedOnTabbingOutToOuterContext() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    myFixture.type("System.getPro");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<HINT text=\"key:\"/><caret>, <hint text=\"def:\"/>), <hint text=\"value:\"/>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<hint text=\"key:\"/>, <HINT text=\"def:\"/><caret>), <hint text=\"value:\"/>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/>System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>)<caret>, <hint text=\"value:\"/>) } }");
    left();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<hint text=\"key:\"/>, <HINT text=\"def:\"/><caret>), <hint text=\"value:\"/>) } }");
  }

  public void testHintPopupContentsForMethodWithOverloads() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<HINT text=\"key:\"/><caret>, <hint text=\"def:\"/>) } }");
    checkHintContents("<html>@NotNull String</html>");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<hint text=\"key:\"/>, <HINT text=\"def:\"/><caret>) } }");
    checkHintContents("<html>String</html>");
    showParameterInfo();
    waitForAllAsyncStuff();
    checkHintContents("<html><font color=gray>@NotNull String key</font color=gray></html>\n" +
                      "<html>@NotNull String key, <b>String def</b></html>");
  }

  public void testHintPopupContentsForMethodWithoutOverloads() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <hint text=\"value:\"/>) } }");
    checkHintContents("<html>@NotNull String</html>");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>, <HINT text=\"value:\"/><caret>) } }");
    checkHintContents("<html>String</html>");
    showParameterInfo();
    waitForAllAsyncStuff();
    checkHintContents("<html>@NotNull String key, <b>String value</b></html>");
  }

  public void testUpInEditor() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkHintContents("<html>@NotNull String</html>");
    showParameterInfo();
    waitForAllAsyncStuff();
    checkHintContents("<html><b>@NotNull String key</b></html>\n" +
                      "<html><b>@NotNull String key</b>, String def</html>");
    myFixture.performEditorAction(IdeActions.ACTION_LOOKUP_UP);
    waitForAllAsyncStuff();
    checkResultWithInlays("<caret>class C { void m() { System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>) } }");
    checkHintContents(null);
  }

  public void testDownInEditor() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkHintContents("<html>@NotNull String</html>");
    showParameterInfo();
    waitForAllAsyncStuff();
    checkHintContents("<html><b>@NotNull String key</b></html>\n" +
                      "<html><b>@NotNull String key</b>, String def</html>");
    myFixture.performEditorAction(IdeActions.ACTION_LOOKUP_DOWN);
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>) } }<caret>");
    checkHintContents(null);
  }

  private void checkResult(String text) {
    myFixture.checkResult(text);
  }

  private void checkResultWithInlays(String text) {
    myFixture.checkResultWithInlays(text);
  }

  private void checkHintContents(String hintText) {
    assertEquals(hintText, myHintFixture.getCurrentHintText());
  }

  private void prev() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREV_PARAMETER);
  }

  private void next() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_PARAMETER);
  }

  private void left() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT);
  }

  private void right() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
  }

  private void down() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
  }

  private void home() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
  }

  private void delete() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE);
  }

  private void configureJava(String text) {
    myFixture.configureByText(JavaFileType.INSTANCE, text);
  }

  private void waitForParameterInfoUpdate() throws TimeoutException {
    ParameterInfoController.waitForDelayedActions(getEditor(), 1, TimeUnit.MINUTES);
  }

  private void showParameterInfo() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO);
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

  private void waitForAutoPopup() throws TimeoutException {
    AutoPopupController.getInstance(getProject()).waitForDelayedActions(1, TimeUnit.MINUTES);
  }

  private void waitForAllAsyncStuff() throws TimeoutException {
    waitForParameterInfoUpdate();
    myFixture.doHighlighting();
    waitTillAnimationCompletes();
    waitForAutoPopup();
  }
}