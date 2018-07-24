// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.StaticallyImportable;
import com.intellij.codeInsight.hints.JavaInlayParameterHintsProvider;
import com.intellij.codeInsight.hints.Option;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.java.codeInsight.AbstractParameterInfoTestCase;
import com.intellij.java.codeInsight.JavaExternalDocumentationTest;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.testFramework.EditorTestUtil;

public class CompletionHintsTest extends AbstractParameterInfoTestCase {
  private boolean myStoredSettingValue;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myStoredSettingValue = CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION;
    CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = true;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
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
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"value:\"/>) } }");

    // check that hints don't disappear after daemon highlighting passes
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"value:\"/>) } }");

    // test Tab/Shift+Tab navigation
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<Hint text=\"key:\"/>, <HINT text=\"value:\"/><caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"value:\"/>) } }");

    // test hints remain shown while entering parameter values
    type("\"a");
    next();
    type("\"b");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<Hint text=\"key:\"/>\"a\", <HINT text=\"value:\"/>\"b<caret>\") } }");

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
    checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <Hint text=\"radix:\"/>) } }");

    // check that hints don't disappear after daemon highlighting passes
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <Hint text=\"radix:\"/>) } }");

    // test Tab/Shift+Tab navigation
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<Hint text=\"digit:\"/>, <HINT text=\"radix:\"/><caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <Hint text=\"radix:\"/>) } }");

    // test hints remain shown while entering parameter values
    type("1");
    next();
    type("2");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<Hint text=\"digit:\"/>1, <HINT text=\"radix:\"/>2<caret>) } }");

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
      checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <Hint text=\"radix:\"/>) } }");

      waitForAllAsyncStuff();
      checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <Hint text=\"radix:\"/>) } }");
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
    methodOverloadDown();
    checkResultWithInlays(
      "class C { void m() { Character.toChars(<HINT text=\"codePoint:\"/><caret>, <Hint text=\"dst:\"/>, <Hint text=\"dstIndex:\"/>) } }");
  }

  public void testSwitchingOverloadsWithParameterFilled() {
    configureJava("class C { void m() { Character.to<caret> } }");
    complete("toChars(int codePoint)");
    type("123");
    checkResultWithInlays("class C { void m() { Character.toChars(<HINT text=\"codePoint:\"/>123<caret>) } }");
    showParameterInfo();
    methodOverloadDown();
    checkResultWithInlays("class C { void m() { Character.toChars(<Hint text=\"codePoint:\"/>123, <HINT text=\"dst:\"/><caret>, <Hint text=\"dstIndex:\"/>) } }");
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
                          "  void m() { some(<HINT text=\"from:\"/><caret>, <Hint text=\"to:\"/>, <Hint text=\"other:\"/>) }\n" +
                          "}");
    showParameterInfo();
    checkHintContents("<html><b>int from</b>, int to</html>\n" +
                      "-\n" +
                      "[<html><b>int from</b>, int to, int other</html>]");
    methodOverloadDown();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "  int some(int from, int to) { return 0; }\n" +
                          "  int some(int from, int to, int other) { return 0; }\n" +
                          "  void m() { some(<HINT text=\"from:\"/><caret>, <Hint text=\"to:\"/>) }\n" +
                          "}");
    checkHintContents("[<html><b>int from</b>, int to</html>]\n" +
                      "-\n" +
                      "<html><b>int from</b>, int to, int other</html>");
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
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"value:\"/>) } }");
    type("System.getPro");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"def:\"/>), <hint text=\"value:\"/>) } }");
  }

  public void testTabWithNestedCompletion() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    type("System.getPro");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"def:\"/>), <hint text=\"value:\"/>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<Hint text=\"key:\"/>, <HINT text=\"def:\"/><caret>), <hint text=\"value:\"/>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/>System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>)<caret>, <Hint text=\"value:\"/>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<Hint text=\"key:\"/>System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>), <HINT text=\"value:\"/><caret>) } }");
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
    checkResultWithInlays("class C { void m() { System.getProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"def:\"/>) } }");
    delete();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<caret> ) } }");
  }

  public void testHintsDisappearWhenNumberOfParametersIsChangedDirectlyWithNoOverloads() throws Exception {
    configureJava("class C { void m() { Character.for<caret> } }");
    complete("forDigit");
    checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <Hint text=\"radix:\"/>) } }");
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
    checkResultWithInlays("class C { void m() { System.setProperty(<Hint text=\"key:\"/>new String().trim(), <HINT text=\"value:\"/><caret>) } }");
  }

  public void testNoHintsDuplicationWhenTypingToTheLeftOfHint() throws Exception {
    configureJava("class C { void m() { Character.for<caret> } }");
    complete("forDigit");
    waitForAllAsyncStuff();
    type("1");
    right();
    type("2");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<Hint text=\"digit:\"/>1,<HINT text=\"radix:\"/>2<caret> ) } }");
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
    checkResultWithInlays("class C { void m() { System.getProperty(<Hint text=\"key:\"/>, <HINT text=\"def:\"/><caret>) } }");
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
    checkResultWithInlays("class C { void m() { System.getProperty(<Hint text=\"key:\"/>\"a\", <HINT text=\"def:\"/>\"b\"<caret>) } }");
  }

  public void testVararg() throws Exception {
    configureJava("class C { void m() { String.for<caret> } }");
    complete();
    checkResultWithInlays("class C { void m() { String.format(<HINT text=\"format:\"/><caret><Hint text=\", args:\"/>) } }");
    type("\"a");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<Hint text=\"format:\"/>\"a\", <HINT text=\"args:\"/><caret>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<hint text=\"format:\"/>\"a\"<hint text=\", args:\"/>)<caret> } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<Hint text=\"format:\"/>\"a\", <HINT text=\"args:\"/><caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<HINT text=\"format:\"/>\"a\"<caret><Hint text=\", args:\"/>) } }");
  }

  public void testVarargWithNoMandatoryArguments() throws Exception {
    configureJava("class C { int vararg(int... args){ return 0; } void m() { varar<caret> } }");
    complete();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int... args){ return 0; } void m() { vararg(<HINT text=\"args:\"/><caret>) } }");
    checkHintContents("<html><b>int...</b></html>");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int... args){ return 0; } void m() { vararg(<Hint text=\"args:\"/>1, <caret>) } }");
    checkHintContents("<html><b>int...</b></html>");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int... args){ return 0; } void m() { vararg(<hint text=\"args:\"/>1)<caret> } }");
    checkHintContents(null);
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int... args){ return 0; } void m() { vararg(<Hint text=\"args:\"/>1, <caret>) } }");
    checkHintContents("<html><b>int...</b></html>");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int... args){ return 0; } void m() { vararg(<HINT text=\"args:\"/>1<caret>) } }");
    checkHintContents("<html><b>int...</b></html>");
  }

  public void testVarargWithTwoMandatoryArguments() throws Exception {
    configureJava("class C { int vararg(int a, int b, int... args){ return 0; } void m() { varar<caret> } }");
    complete();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<HINT text=\"a:\"/><caret>, <Hint text=\"b:\"/><Hint text=\", args:\"/>) } }");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/><caret><Hint text=\", args:\"/>) } }");
    type("2");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<Hint text=\"a:\"/>1, <Hint text=\"b:\"/>2, <HINT text=\"args:\"/><caret>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<hint text=\"a:\"/>1, <hint text=\"b:\"/>2<hint text=\", args:\"/>)<caret> } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<Hint text=\"a:\"/>1, <Hint text=\"b:\"/>2, <HINT text=\"args:\"/><caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/>2<caret><Hint text=\", args:\"/>) } }");
  }

  public void testVarargHintsDontSwitchPlaces() throws Exception {
    configureJava("class C { void m() { java.util.Collections.add<caret> } }");
    complete();
    checkResultWithInlays("class C { void m() { java.util.Collections.addAll(<HINT text=\"c:\"/><caret><Hint text=\", elements:\"/>) } }");
    left();
    type('s');
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { java.util.Collections.addAll(<HINT text=\"c:\"/>s<caret><Hint text=\", elements:\"/>) } }");
    backspace();
    checkResultWithInlays("class C { void m() { java.util.Collections.addAll(<HINT text=\"c:\"/><caret><Hint text=\", elements:\"/>) } }");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { java.util.Collections.addAll(<HINT text=\"c:\"/><caret><Hint text=\", elements:\"/>) } }");
  }

  public void testHintsDontDisappearWhenNavigatingAwayFromUncompletedInvocation() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"value:\"/>) } }");
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
                          "        super.method(<HINT text=\"a:\"/><caret>, <Hint text=\"b:\"/>);\n" +
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
      checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <Hint text=\"radix:\"/>) } }");

      // check that hints don't disappear after daemon highlighting passes
      waitForAllAsyncStuff();
      checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <Hint text=\"radix:\"/>) } }");

      // test Tab/Shift+Tab navigation
      next();
      waitForAllAsyncStuff();
      checkResultWithInlays("class C { void m() { Character.forDigit(<Hint text=\"digit:\"/>, <HINT text=\"radix:\"/><caret>) } }");
      prev();
      waitForAllAsyncStuff();
      checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <Hint text=\"radix:\"/>) } }");

      // test hints remain shown while entering parameter values
      type("1");
      next();
      type("2");
      waitForAllAsyncStuff();
      checkResultWithInlays("class C { void m() { Character.forDigit(<Hint text=\"digit:\"/>1, <HINT text=\"radix:\"/>2<caret>) } }");

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
                          "    void m2() { mmm(<HINT text=\"a:\"/><caret>, <Hint text=\"b:\"/>, <Hint text=\"c:\"/>, <Hint text=\"d:\"/>, <Hint text=\"e:\"/>, <Hint text=\"f:\"/>); }\n" +
                          "}");
  }
  
  public void testNestedContextIsNotDisposedOnTabbingOutToOuterContext() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    type("System.getPro");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"def:\"/>), <hint text=\"value:\"/>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<Hint text=\"key:\"/>, <HINT text=\"def:\"/><caret>), <hint text=\"value:\"/>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/>System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>)<caret>, <Hint text=\"value:\"/>) } }");
    left();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<Hint text=\"key:\"/>, <HINT text=\"def:\"/><caret>), <hint text=\"value:\"/>) } }");
  }

  public void testHintPopupContentsForMethodWithOverloads() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"def:\"/>) } }");
    checkHintContents("<html><b>@NotNull String</b>&nbsp;&nbsp;<i>the name of the system property.  </i></html>");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<Hint text=\"key:\"/>, <HINT text=\"def:\"/><caret>) } }");
    checkHintContents("<html><b>String</b>&nbsp;&nbsp;<i>a default value.  </i></html>");
    showParameterInfo();
    waitForAllAsyncStuff();
    checkHintContents("<html><font color=gray>@NotNull String key</font color=gray></html>\n" +
                      "-\n" +
                      "[<html>@NotNull String key, <b>String def</b></html>]");
  }

  public void testHintPopupContentsForMethodWithoutOverloads() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"value:\"/>) } }");
    checkHintContents("<html><b>@NotNull String</b>&nbsp;&nbsp;<i>the name of the system property.  </i></html>");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<Hint text=\"key:\"/>, <HINT text=\"value:\"/><caret>) } }");
    checkHintContents("<html><b>String</b>&nbsp;&nbsp;<i>the value of the system property.  </i></html>");
    showParameterInfo();
    waitForAllAsyncStuff();
    checkHintContents("<html>@NotNull String key, <b>String value</b></html>");
  }

  public void testUpInEditor() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>@NotNull String</b>&nbsp;&nbsp;<i>the name of the system property.  </i></html>");
    showParameterInfo();
    waitForAllAsyncStuff();
    checkHintContents("<html><b>@NotNull String key</b></html>\n" +
                      "-\n" +
                      "[<html><b>@NotNull String key</b>, String def</html>]");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
    waitForAllAsyncStuff();
    checkResultWithInlays("<caret>class C { void m() { System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>) } }");
    checkHintContents(null);
  }

  public void testDownInEditor() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>@NotNull String</b>&nbsp;&nbsp;<i>the name of the system property.  </i></html>");
    showParameterInfo();
    waitForAllAsyncStuff();
    checkHintContents("<html><b>@NotNull String key</b></html>\n" +
                      "-\n" +
                      "[<html><b>@NotNull String key</b>, String def</html>]");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>) } }<caret>");
    checkHintContents(null);
  }

  public void testPopupAfterCaretMovesOutsideOfParenthesis() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>@NotNull String</b>&nbsp;&nbsp;<i>the name of the system property.  </i></html>");
    left();
    left();
    left();
    waitForAllAsyncStuff();
    checkHintContents(null);
  }

  public void testNoLineUnderPopupText() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key)");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>@NotNull String</b>&nbsp;&nbsp;<i>the name of the system property.  </i></html>");
  }

  public void testSwitchIsPossibleForManuallyEnteredUnmatchedMethodCall() throws Exception {
    configureJava("class C {\n" +
                  "  void a(int p, int q) {}\n" +
                  "  void a(int p, int q, int r) {}\n" +
                  "  void m() { a(<caret>) }\n" +
                  "}");
    showParameterInfo();
    checkHintContents("<html><b>int p</b>, int q</html>\n" +
                      "-\n" +
                      "<html><b>int p</b>, int q, int r</html>");
    methodOverloadDown();
    checkResultWithInlays("class C {\n" +
                          "  void a(int p, int q) {}\n" +
                          "  void a(int p, int q, int r) {}\n" +
                          "  void m() { a(<HINT text=\"p:\"/><caret>, <Hint text=\"q:\"/>) }\n" +
                          "}");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "  void a(int p, int q) {}\n" +
                          "  void a(int p, int q, int r) {}\n" +
                          "  void m() { a(<Hint text=\"p:\"/>, <HINT text=\"q:\"/><caret>) }\n" +
                          "}");
  }

  public void testShortHintIsShownAfterFullHint() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    showParameterInfo();
    checkHintContents("<html><b>@NotNull String key</b></html>\n" +
                      "-\n" +
                      "[<html><b>@NotNull String key</b>, String def</html>]");
    escape();
    checkHintContents(null);
    right();
    waitForAllAsyncStuff();
    checkHintContents("<html><b>String</b>&nbsp;&nbsp;<i>a default value.  </i></html>");
  }

  public void testAutopopupIsShownWithCompletionHintsDisabled() throws Exception {
    CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = false;
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    checkResultWithInlays("class C { void m() { System.getProperty(<caret>) } }");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>@NotNull String key</b></html>\n" +
                      "-\n" +
                      "[<html><b>@NotNull String key</b>, String def</html>]");
  }

  public void testFullPopupIsHiddenOnTyping() {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    showParameterInfo();
    type(' ');
    checkHintContents(null);
  }

  public void testFullPopupIsHiddenOnTypingAfterOverloadSwitch() {
    configureJava("class C { void m() { System.getProperty(\"a\"<caret>) } }");
    showParameterInfo();
    methodOverloadDown();
    type("\"b");
    checkHintContents(null);
  }

  public void testNextParameterWorksWhenTabCompletionDoesntChangeAnything() throws Exception {
    configureJava("class C { void m() { String local = \"a\"; String local2 = \"b\"; System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    type("local");
    complete();
    assertEquals("local", myFixture.getLookupElements()[0].getLookupString());
    myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE);
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String local = \"a\"; String local2 = \"b\"; System.getProperty(<Hint text=\"key:\"/>local, <HINT text=\"def:\"/><caret>) } }");
  }

  public void testGenericType() throws Exception {
    configureJava("class C { void abcd(Class<?> c) {} void m() { abc<caret> } }");
    complete();
    waitForAllAsyncStuff();
    checkHintContents("<html><b>Class&lt;?&gt;</b></html>");
  }

  public void testCompletionBetweenVarargHints() {
    configureJava("class C { int myVal = 1; void vararg(int a, int... b) {} void m() { varar<caret> } }");
    complete();
    checkResultWithInlays("class C { int myVal = 1; void vararg(int a, int... b) {} void m() { vararg(<HINT text=\"a:\"/><caret><Hint text=\", b:\"/>); } }");
    type("myVa");
    complete();
    checkResultWithInlays("class C { int myVal = 1; void vararg(int a, int... b) {} void m() { vararg(<HINT text=\"a:\"/>myVal<caret><Hint text=\", b:\"/>); } }");
  }

  public void testEnteringSpaceBetweenVarargHints() throws Exception {
    configureJava("class C { void vararg(Object a, int... b) {} void m() { varar<caret> } }");
    complete();
    checkResultWithInlays("class C { void vararg(Object a, int... b) {} void m() { vararg(<HINT text=\"a:\"/><caret><Hint text=\", b:\"/>); } }");
    type("new ");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void vararg(Object a, int... b) {} void m() { vararg(<HINT text=\"a:\"/>new <caret><Hint text=\", b:\"/>); } }");
  }

  public void testNoTooltipForInvalidParameter() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    type("\"a");
    next();
    type("\"b\",");
    waitForAllAsyncStuff();
    checkHintContents(null);
  }

  public void testIncorrectTooltipIsNotShownForInnerContext() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    type("new String(\"");
    waitForAllAsyncStuff();
    checkHintContents(null);
  }

  public void testOverloadsWithOneAndNoParameters() throws Exception {
    configureJava("class C { void method() {} void method(int a) {} void m() { m<caret> } }");
    complete("method(int a)");
    checkResultWithInlays("class C { void method() {} void method(int a) {} void m() { method(<HINT text=\"a:\"/><caret>); } }");
    home();
    waitForAllAsyncStuff();
    checkResultWithInlays("<caret>class C { void method() {} void method(int a) {} void m() { method(); } }");
  }

  public void testCodeFragment() {
    PsiExpressionCodeFragment fragment =
      JavaCodeFragmentFactory.getInstance(getProject()).createExpressionCodeFragment("System.getPro<caret>", null, null, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    complete("getProperty(String key, String def)");
    checkResultWithInlays("System.getProperty(<caret>)"); // At the moment, we assure that neither hints, nor comma appear.
                                                          // Later we might make it work correctly for code fragments.
  }

  public void testVarargWithNoMandatoryArgumentsDoesNotKeepHintOnCaretOut() throws Exception {
    configureJava("class C { int vararg(int... args){ return 0; } void m() { varar<caret> } }");
    complete();
    checkResultWithInlays("class C { int vararg(int... args){ return 0; } void m() { vararg(<HINT text=\"args:\"/><caret>) } }");
    home();
    waitForAllAsyncStuff();
    checkResultWithInlays("<caret>class C { int vararg(int... args){ return 0; } void m() { vararg() } }");
  }

  public void testNoLinksInParameterJavadoc() throws Exception {
    configureJava("class C { void m() { String.for<caret> } }");
    complete();
    checkResultWithInlays("class C { void m() { String.format(<HINT text=\"format:\"/><caret><Hint text=\", args:\"/>) } }");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>String</b>&nbsp;&nbsp;<i>         A format string  </i></html>");
  }

  public void testBasicScenarioForConstructor() throws Exception {
    // check hints appearance on completion
    configureJava("class C { C(int a, int b) {} void m() { new C<caret> } }");
    complete("C(int a, int b)");
    checkResultWithInlays("class C { C(int a, int b) {} void m() { new C(<HINT text=\"a:\"/><caret>, <Hint text=\"b:\"/>) } }");
    // check that hints don't disappear after daemon highlighting passes
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { C(int a, int b) {} void m() { new C(<HINT text=\"a:\"/><caret>, <Hint text=\"b:\"/>) } }");

    // test Tab/Shift+Tab navigation
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { C(int a, int b) {} void m() { new C(<Hint text=\"a:\"/>, <HINT text=\"b:\"/><caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { C(int a, int b) {} void m() { new C(<HINT text=\"a:\"/><caret>, <Hint text=\"b:\"/>) } }");

    // test hints remain shown while entering parameter values
    type("1");
    next();
    type("2");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { C(int a, int b) {} void m() { new C(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/>2<caret>) } }");

    // test hints don't disappear when caret moves out of parameter list
    right();
    right();
    right();
    right();

    waitForAllAsyncStuff();
    checkResultWithInlays("class C { C(int a, int b) {} void m() { new C(<hint text=\"a:\"/>1, <hint text=\"b:\"/>2) } <caret>}");
  }

  public void testEnumValueOf() throws Exception {
    configureJava("class C { void m() { Thread.State.<caret> } }");
    complete("valueOf(String name)");
    checkResultWithInlays("class C { void m() { Thread.State.valueOf(<HINT text=\"name:\"/><caret>) } }");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>String</b></html>");
  }

  public void testBrokenPsiCall() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"value:\"/>) } }");
    type(';');
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(;<caret>, ) } }");
    backspace();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"value:\"/>) } }");
  }

  public void testKeepHintsLonger() throws Exception {
    RegistryValue setting = Registry.get("editor.keep.completion.hints.longer");
    boolean oldValue = setting.asBoolean();
    try {
      setting.setValue(true);
      configureJava("class C {\n" +
                    "  void m() { System.setPro<caret> }\n" +
                    "}");
      complete("setProperty");
      checkResultWithInlays("class C {\n" +
                            "  void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"value:\"/>) }\n" +
                            "}");
      type("\"a");
      next();
      type("\"b");
      home();
      waitForAllAsyncStuff();
      checkResultWithInlays("class C {\n" +
                            "  <caret>void m() { System.setProperty(<hint text=\"key:\"/>\"a\", <hint text=\"value:\"/>\"b\") }\n" +
                            "}");
      textStart();
      waitForAllAsyncStuff();
      checkResultWithInlays("<caret>class C {\n" +
                            "  void m() { System.setProperty(\"a\", \"b\") }\n" +
                            "}");
    }
    finally {
      setting.setValue(oldValue);
    }
  }

  public void testKeepHintsEvenLonger() throws Exception {
    RegistryValue setting = Registry.get("editor.keep.completion.hints.even.longer");
    boolean oldValue = setting.asBoolean();
    try {
      setting.setValue(true);
      configureJava("class C {\n\n\n\n\n\n" +
                    "  void m() { System.setPro<caret> }\n" +
                    "}");
      EditorTestUtil.setEditorVisibleSize(getEditor(), 1000, 3);
      complete("setProperty");
      checkResultWithInlays("class C {\n\n\n\n\n\n" +
                            "  void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"value:\"/>) }\n" +
                            "}");
      type("\"a");
      next();
      type("\"b");
      home();
      waitForAllAsyncStuff();
      checkResultWithInlays("class C {\n\n\n\n\n\n" +
                            "  <caret>void m() { System.setProperty(<hint text=\"key:\"/>\"a\", <hint text=\"value:\"/>\"b\") }\n" +
                            "}");
      up();
      waitForAllAsyncStuff();
      checkResultWithInlays("class C {\n\n\n\n\n<caret>\n" +
                            "  void m() { System.setProperty(<hint text=\"key:\"/>\"a\", <hint text=\"value:\"/>\"b\") }\n" +
                            "}");
      textStart();
      waitForAllAsyncStuff();
      checkResultWithInlays("<caret>class C {\n\n\n\n\n\n" +
                            "  void m() { System.setProperty(\"a\", \"b\") }\n" +
                            "}");
    }
    finally {
      setting.setValue(oldValue);
    }
  }

  public void testOverloadWithNoParameters() throws Exception {
    configureJava("class C { void m() { System.out.pr<caret> } }");
    complete("println(long x)");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>long</b>&nbsp;&nbsp;<i>a The <code>long</code> to be printed.</i></html>");
  }

  public void testSecondCtrlPShowsHints() {
    configureJava("class C { void m() { System.setProperty(\"a\", \"<caret>b\"); } }");
    showParameterInfo();
    showParameterInfo();
    checkResultWithInlays("class C { void m() { System.setProperty(<Hint text=\"key:\"/>\"a\", <HINT text=\"value:\"/>\"<caret>b\"); } }");
  }

  public void testQuickDocForOverloadSelectedOnCompletion() throws Exception {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key)");
    checkResult("class C { void m() { System.getProperty(<caret>) } }");
    String doc = JavaExternalDocumentationTest.getDocumentationText(getEditor());
    assertTrue(doc.contains("<code>null</code> if there is no property with that key"));
  }

  public void testHighlightingOfHintsOnMultipleLines() throws Exception {
    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    next();
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER);
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<Hint text=\"key:\"/>, \n" +
                          "        <HINT text=\"value:\"/><caret>) } }");
  }

  public void testGlobalStaticMethodCompletion() {
    configureJava("class C { void m() { arraycop<caret> } }");
    complete();
    complete();
    assertSize(1, myItems);
    LookupElement item = myItems[0];
    item.as(StaticallyImportable.CLASS_CONDITION_KEY).setShouldBeImported(true); // emulate 'Import statically' intention
    selectItem(item, Lookup.NORMAL_SELECT_CHAR);
    checkResultWithInlays("import static java.lang.System.arraycopy;\n\nclass C { void m() { arraycopy(<HINT text=\"src:\"/><caret>, <Hint text=\"srcPos:\"/>, <Hint text=\"dest:\"/>, <Hint text=\"destPos:\"/>, <Hint text=\"length:\"/>); } }");
  }

  public void testParameterHintsLimit() throws Exception {
    setParameterHintsLimit(2);
    configureJava("class C {\n" +
                  "    int mmm(int a, int b, int c) { return 0; }\n" +
                  "    void m2() { mm<caret> }\n" +
                  "}");
    complete();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<HINT text=\"a:\"/><caret>, <Hint text=\"b:\"/><Hint text=\"...more:\"/>) }\n" +
                          "}");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>, <HINT text=\"b:\"/><caret><Hint text=\"...more:\"/>) }\n" +
                          "}");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>, <Hint text=\"b:\"/>, <HINT text=\"c:\"/><caret>) }\n" +
                          "}");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<hint text=\"a:\"/>, <hint text=\"b:\"/>, <hint text=\"c:\"/>)<caret> }\n" +
                          "}");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>, <Hint text=\"b:\"/>, <HINT text=\"c:\"/><caret>) }\n" +
                          "}");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>, <HINT text=\"b:\"/><caret><Hint text=\"...more:\"/>) }\n" +
                          "}");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<HINT text=\"a:\"/><caret>, <Hint text=\"b:\"/><Hint text=\"...more:\"/>) }\n" +
                          "}");
  }

  public void testParameterHintsLimitWithTyping() throws Exception {
    setParameterHintsLimit(2);
    configureJava("class C {\n" +
                  "    int mmm(int a, int b, int c) { return 0; }\n" +
                  "    void m2() { mm<caret> }\n" +
                  "}");
    complete();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<HINT text=\"a:\"/><caret>, <Hint text=\"b:\"/><Hint text=\"...more:\"/>) }\n" +
                          "}");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/><caret><Hint text=\"...more:\"/>) }\n" +
                          "}");
    type("2");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>1, <Hint text=\"b:\"/>2, <HINT text=\"c:\"/><caret>) }\n" +
                          "}");
    type("3");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<hint text=\"a:\"/>1, <hint text=\"b:\"/>2, <hint text=\"c:\"/>3)<caret> }\n" +
                          "}");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>1, <Hint text=\"b:\"/>2, <HINT text=\"c:\"/>3<caret>) }\n" +
                          "}");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/>2<caret>, <Hint text=\"c:\"/>3) }\n" +
                          "}");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c) { return 0; }\n" +
                          "    void m2() { mmm(<HINT text=\"a:\"/>1<caret>, <Hint text=\"b:\"/>2, <Hint text=\"c:\"/>3) }\n" +
                          "}");
  }

  public void testParameterHintsLimitMoreParameters() throws Exception {
    setParameterHintsLimit(2);
    configureJava("class C {\n" +
                  "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                  "    void m2() { mm<caret> }\n" +
                  "}");
    complete();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<HINT text=\"a:\"/><caret>, <Hint text=\"b:\"/><Hint text=\"...more:\"/>) }\n" +
                          "}");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/><caret><Hint text=\"...more:\"/>) }\n" +
                          "}");
    type("2");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>1, <Hint text=\"b:\"/>2, <HINT text=\"c:\"/><caret><Hint text=\"...more:\"/>) }\n" +
                          "}");
    type("3");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>1, <Hint text=\"b:\"/>2, <Hint text=\"c:\"/>3, <HINT text=\"d:\"/><caret>) }\n" +
                          "}");
    type("4");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<hint text=\"a:\"/>1, <hint text=\"b:\"/>2, <hint text=\"c:\"/>3, <hint text=\"d:\"/>4)<caret> }\n" +
                          "}");
  }

  public void testVarargWithLimit() throws Exception {
    setParameterHintsLimit(1);

    configureJava("class C { void m() { String.for<caret> } }");
    complete();
    checkResultWithInlays("class C { void m() { String.format(<HINT text=\"format:\"/><caret><Hint text=\", args:\"/>) } }");
    type("\"a");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<Hint text=\"format:\"/>\"a\", <HINT text=\"args:\"/><caret>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<hint text=\"format:\"/>\"a\"<hint text=\", args:\"/>)<caret> } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<Hint text=\"format:\"/>\"a\", <HINT text=\"args:\"/><caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<HINT text=\"format:\"/>\"a\"<caret><Hint text=\", args:\"/>) } }");
  }

  public void testBlacklistedHintsDoNotAppearWithCompletionHintsDisabled() throws Exception {
    CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = false;
    configureJava("class C { \n" +
                  "  void something() {}\n" +
                  "  void some(int begin) {}\n" +
                  "  void some(int begin, int end) {}\n" +
                  "  void m() { som<caret> }\n" +
                  "}");
    complete("some(int begin)");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { \n" +
                          "  void something() {}\n" +
                          "  void some(int begin) {}\n" +
                          "  void some(int begin, int end) {}\n" +
                          "  void m() { some(<caret>); }\n" +
                          "}");
    type("0, 1");
    home();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { \n" +
                          "  void something() {}\n" +
                          "  void some(int begin) {}\n" +
                          "  void some(int begin, int end) {}\n" +
                          "  <caret>void m() { some(0, 1); }\n" +
                          "}");
  }

  public void testVirtualCommaBasicCase() throws Exception {
    enableVirtualComma();

    configureJava("class C { int mmm(short a, int b, long c){ return 0; } void m() { mm<caret> } }");
    complete();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<HINT text=\"a:\"/><caret><Hint text=\", b:\"/><Hint text=\", c:\"/>) } }");
    checkHintContents("<html><b>short</b></html>");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/><caret><Hint text=\", c:\"/>) } }");
    checkHintContents("<html><b>int</b></html>");
    type("2");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<Hint text=\"a:\"/>1, <Hint text=\"b:\"/>2, <HINT text=\"c:\"/><caret>) } }");
    checkHintContents("<html><b>long</b></html>");
    type("3");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<hint text=\"a:\"/>1, <hint text=\"b:\"/>2, <hint text=\"c:\"/>3)<caret> } }");
    checkHintContents(null);
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<Hint text=\"a:\"/>1, <Hint text=\"b:\"/>2, <HINT text=\"c:\"/>3<caret>) } }");
    checkHintContents("<html><b>long</b></html>");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/>2<caret>, <Hint text=\"c:\"/>3) } }");
    checkHintContents("<html><b>int</b></html>");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<HINT text=\"a:\"/>1<caret>, <Hint text=\"b:\"/>2, <Hint text=\"c:\"/>3) } }");
    checkHintContents("<html><b>short</b></html>");
  }

  public void testVirtualCommaEmptyParams() throws Exception {
    enableVirtualComma();

    configureJava("class C { int mmm(short a, int b, long c){ return 0; } void m() { mm<caret> } }");
    complete();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<HINT text=\"a:\"/><caret><Hint text=\", b:\"/><Hint text=\", c:\"/>) } }");
    checkHintContents("<html><b>short</b></html>");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<Hint text=\"a:\"/>, <HINT text=\"b:\"/><caret><Hint text=\", c:\"/>) } }");
    checkHintContents("<html><b>int</b></html>");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<Hint text=\"a:\"/>, <Hint text=\"b:\"/>, <HINT text=\"c:\"/><caret>) } }");
    checkHintContents("<html><b>long</b></html>");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<hint text=\"a:\"/>, <hint text=\"b:\"/>, <hint text=\"c:\"/>)<caret> } }");
    checkHintContents(null);
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<Hint text=\"a:\"/>, <Hint text=\"b:\"/>, <HINT text=\"c:\"/><caret>) } }");
    checkHintContents("<html><b>long</b></html>");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<Hint text=\"a:\"/>, <HINT text=\"b:\"/><caret><Hint text=\", c:\"/>) } }");
    checkHintContents("<html><b>int</b></html>");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<HINT text=\"a:\"/><caret><Hint text=\", b:\"/><Hint text=\", c:\"/>) } }");
    checkHintContents("<html><b>short</b></html>");
  }

  public void testVirtualCommaWithLimit() throws Exception {
    enableVirtualComma();
    setParameterHintsLimit(2);

    configureJava("class C {\n" +
                  "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                  "    void m2() { mm<caret> }\n" +
                  "}");
    complete();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<HINT text=\"a:\"/><caret><Hint text=\", b:\"/><Hint text=\"...more:\"/>) }\n" +
                          "}");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/><caret><Hint text=\"...more:\"/>) }\n" +
                          "}");
    type("2");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>1, <Hint text=\"b:\"/>2, <HINT text=\"c:\"/><caret><Hint text=\"...more:\"/>) }\n" +
                          "}");
    type("3");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>1, <Hint text=\"b:\"/>2, <Hint text=\"c:\"/>3, <HINT text=\"d:\"/><caret>) }\n" +
                          "}");
    type("4");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<hint text=\"a:\"/>1, <hint text=\"b:\"/>2, <hint text=\"c:\"/>3, <hint text=\"d:\"/>4)<caret> }\n" +
                          "}");
  }

  public void testVirtualCommaWithLimitEmptyParams() throws Exception {
    enableVirtualComma();
    setParameterHintsLimit(2);

    configureJava("class C {\n" +
                  "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                  "    void m2() { mm<caret> }\n" +
                  "}");
    complete();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<HINT text=\"a:\"/><caret><Hint text=\", b:\"/><Hint text=\"...more:\"/>) }\n" +
                          "}");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>, <HINT text=\"b:\"/><caret><Hint text=\"...more:\"/>) }\n" +
                          "}");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>, <Hint text=\"b:\"/>, <HINT text=\"c:\"/><caret><Hint text=\"...more:\"/>) }\n" +
                          "}");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>, <Hint text=\"b:\"/>, <Hint text=\"c:\"/>, <HINT text=\"d:\"/><caret>) }\n" +
                          "}");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<hint text=\"a:\"/>, <hint text=\"b:\"/>, <hint text=\"c:\"/>, <hint text=\"d:\"/>)<caret> }\n" +
                          "}");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>, <Hint text=\"b:\"/>, <Hint text=\"c:\"/>, <HINT text=\"d:\"/><caret>) }\n" +
                          "}");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>, <Hint text=\"b:\"/>, <HINT text=\"c:\"/><caret><Hint text=\"...more:\"/>) }\n" +
                          "}");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<Hint text=\"a:\"/>, <HINT text=\"b:\"/><caret><Hint text=\"...more:\"/>) }\n" +
                          "}");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C {\n" +
                          "    int mmm(int a, int b, int c, int d) { return 0; }\n" +
                          "    void m2() { mmm(<HINT text=\"a:\"/><caret><Hint text=\", b:\"/><Hint text=\"...more:\"/>) }\n" +
                          "}");
  }

  public void testVirtualCommaWithOverload() throws Exception {
    enableVirtualComma();

    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    checkResultWithInlays("class C { void m() { System.getProperty(<HINT text=\"key:\"/><caret><Hint text=\", def:\"/>) } }");
    type("\"a");
    waitForAllAsyncStuff();
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<Hint text=\"key:\"/>\"a\", <HINT text=\"def:\"/><caret>) } }");
    type("\"b");
    waitForAllAsyncStuff();
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<hint text=\"key:\"/>\"a\", <hint text=\"def:\"/>\"b\")<caret> } }");
  }

  private void checkResultWithInlays(String text) {
    myFixture.checkResultWithInlays(text);
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

  private void up() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
  }

  private void methodOverloadDown() {
    myFixture.performEditorAction(IdeActions.ACTION_METHOD_OVERLOAD_SWITCH_DOWN);
  }

  private void home() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
  }

  private void textStart() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TEXT_START);
  }

  private void delete() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE);
  }

  private void backspace() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
  }

  private void escape() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ESCAPE);
  }

  private void setParameterHintsLimit(int limit) {
    RegistryValue registryValue = Registry.get("editor.completion.hints.per.call.limit");
    int storedValue = registryValue.asInteger();
    registryValue.setValue(limit);
    Disposer.register(getTestRootDisposable(), () -> registryValue.setValue(storedValue));
  }

  private void enableVirtualComma() {
    RegistryValue registryValue = Registry.get("editor.completion.hints.virtual.comma");
    boolean storedValue = registryValue.asBoolean();
    registryValue.setValue(true);
    Disposer.register(getTestRootDisposable(), () -> registryValue.setValue(storedValue));
  }
}