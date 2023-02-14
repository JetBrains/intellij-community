// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.StaticallyImportable;
import com.intellij.codeInsight.hints.JavaInlayParameterHintsProvider;
import com.intellij.codeInsight.hints.Option;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.java.codeInsight.AbstractParameterInfoTestCase;
import com.intellij.java.codeInsight.JavaExternalDocumentationTest;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.EditorMouseFixture;

import java.util.LinkedHashMap;

@NeedsIndex.SmartMode(reason = "Hints shouldn't work in dumb mode")
public class CompletionHintsTest extends AbstractParameterInfoTestCase {
  private boolean myStoredParamHintsValue;
  private boolean myStoredTabOutValue;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    myStoredParamHintsValue = settings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION;
    myStoredTabOutValue = settings.TAB_EXITS_BRACKETS_AND_QUOTES;
    settings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = true;
    settings.TAB_EXITS_BRACKETS_AND_QUOTES = false;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings settings = CodeInsightSettings.getInstance();
      settings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = myStoredParamHintsValue;
      settings.TAB_EXITS_BRACKETS_AND_QUOTES = myStoredTabOutValue;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testBasicScenarioWithHintsDisabledForMethod() {
    disableVirtualComma();

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

  public void testBasicScenarioWithHintsEnabledForMethod() {
    disableVirtualComma();

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

  public void testWithHintsEnabledForNonLiterals() {
    disableVirtualComma();

    Option option = JavaInlayParameterHintsProvider.Companion.getInstance().getShowForParamsWithSameType();
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
    disableVirtualComma();

    configureJava("class C { void m() { Character.to<caret> } }");
    complete("toChars(int codePoint)");
    checkResultWithInlays("class C { void m() { Character.toChars(<HINT text=\"codePoint:\"/><caret>) } }");
    waitForAutoPopup();
    showParameterInfo();
    methodOverloadDown();
    checkResultWithInlays(
      "class C { void m() { Character.toChars(<HINT text=\"codePoint:\"/><caret>, <Hint text=\"dst:\"/>, <Hint text=\"dstIndex:\"/>) } }");
  }

  public void testSwitchingOverloadsWithParameterFilled() {
    disableVirtualComma();

    configureJava("class C { void m() { Character.to<caret> } }");
    complete("toChars(int codePoint)");
    type("123");
    checkResultWithInlays("class C { void m() { Character.toChars(<HINT text=\"codePoint:\"/>123<caret>) } }");
    waitForAutoPopup();
    showParameterInfo();
    methodOverloadDown();
    checkResultWithInlays("class C { void m() { Character.toChars(<Hint text=\"codePoint:\"/>123, <HINT text=\"dst:\"/><caret>, <Hint text=\"dstIndex:\"/>) } }");
  }

  public void testSwitchingOverloadsForMethodWithDisabledHints() {
    disableVirtualComma();

    configureJava("""
                    class C {
                      int some(int from, int to) { return 0; }
                      int some(int from, int to, int other) { return 0; }
                      void m() { s<caret> }
                    }""");
    complete("some(int from, int to, int other)");
    checkResultWithInlays("""
                            class C {
                              int some(int from, int to) { return 0; }
                              int some(int from, int to, int other) { return 0; }
                              void m() { some(<HINT text="from:"/><caret>, <Hint text="to:"/>, <Hint text="other:"/>) }
                            }""");
    waitForAutoPopup();
    showParameterInfo();
    checkHintContents("""
                        <html><b>int from</b>, int to</html>
                        -
                        [<html><b>int from</b>, int to, int other</html>]""");
    methodOverloadDown();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                              int some(int from, int to) { return 0; }
                              int some(int from, int to, int other) { return 0; }
                              void m() { some(<HINT text="from:"/><caret>, <Hint text="to:"/>) }
                            }""");
    checkHintContents("""
                        [<html><b>int from</b>, int to</html>]
                        -
                        <html><b>int from</b>, int to, int other</html>""");
  }

  public void testNoHintsForMethodReference() {
    disableVirtualComma();

    configureJava("""
                    class C {
                      interface I { void i(int p); }
                      void referenced(int a) {}
                      void m(I lambda) {}
                      void m2() { m(this::<caret>) }
                    }""");
    complete("referenced");
    checkResultWithInlays("""
                            class C {
                              interface I { void i(int p); }
                              void referenced(int a) {}
                              void m(I lambda) {}
                              void m2() { m(this::referenced<caret>) }
                            }""");
  }

  public void testNestedCompletion() {
    disableVirtualComma();

    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"value:\"/>) } }");
    type("System.getPro");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<hint text=\"key:\"/>System.getProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"def:\"/>), <hint text=\"value:\"/>) } }");
  }

  public void testTabWithNestedCompletion() {
    disableVirtualComma();

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
    disableVirtualComma();

    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key)");
    checkResultWithInlays("class C { void m() { System.getProperty(<caret>) } }");
  }

  public void testHintsDisappearWhenNumberOfParametersIsChangedDirectly() {
    disableVirtualComma();

    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    checkResultWithInlays("class C { void m() { System.getProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"def:\"/>) } }");
    delete();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<caret> ) } }");
  }

  public void testHintsDisappearWhenNumberOfParametersIsChangedDirectlyWithNoOverloads() {
    disableVirtualComma();

    configureJava("class C { void m() { Character.for<caret> } }");
    complete("forDigit");
    checkResultWithInlays("class C { void m() { Character.forDigit(<HINT text=\"digit:\"/><caret>, <Hint text=\"radix:\"/>) } }");
    delete();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.forDigit(<caret> ) } }");
  }

  public void testCaretIsToTheRightOfHintAfterSmartInnerCompletion() {
    disableVirtualComma();

    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    type("new String().trim");
    completeSmart();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<Hint text=\"key:\"/>new String().trim(), <HINT text=\"value:\"/><caret>) } }");
  }

  public void testNoHintsDuplicationWhenTypingToTheLeftOfHint() {
    disableVirtualComma();

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

  public void testPrevParameterFromOutside() {
    disableVirtualComma();

    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    next();
    next();
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<Hint text=\"key:\"/>, <HINT text=\"def:\"/><caret>) } }");
  }

  public void testPrevParameterFromOutsideWhenParametersAreNotEmpty() {
    disableVirtualComma();

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

  public void testVararg() {
    disableVirtualComma();

    configureJava("class C { void m() { String.f<caret> } }");
    complete("format(String format, Object... args)");
    checkResultWithInlays("class C { void m() { String.format(<HINT text=\"format:\"/><caret><Hint text=\",args:\"/>) } }");
    type("\"a");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<Hint text=\"format:\"/>\"a\", <HINT text=\"args:\"/><caret>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<hint text=\"format:\"/>\"a\"<hint text=\",args:\"/>)<caret> } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<Hint text=\"format:\"/>\"a\", <HINT text=\"args:\"/><caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<HINT text=\"format:\"/>\"a\"<caret><Hint text=\",args:\"/>) } }");
  }

  public void testVarargWithNoMandatoryArguments() {
    disableVirtualComma();

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

  public void testVarargWithTwoMandatoryArguments() {
    disableVirtualComma();

    configureJava("class C { int vararg(int a, int b, int... args){ return 0; } void m() { varar<caret> } }");
    complete();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<HINT text=\"a:\"/><caret>, <Hint text=\"b:\"/><Hint text=\",args:\"/>) } }");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/><caret><Hint text=\",args:\"/>) } }");
    type("2");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<Hint text=\"a:\"/>1, <Hint text=\"b:\"/>2, <HINT text=\"args:\"/><caret>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<hint text=\"a:\"/>1, <hint text=\"b:\"/>2<hint text=\",args:\"/>)<caret> } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<Hint text=\"a:\"/>1, <Hint text=\"b:\"/>2, <HINT text=\"args:\"/><caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int vararg(int a, int b, int... args){ return 0; } void m() { vararg(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/>2<caret><Hint text=\",args:\"/>) } }");
  }

  public void testVarargHintsDontSwitchPlaces() {
    disableVirtualComma();

    configureJava("class C { void m() { java.util.Collections.add<caret> } }");
    complete();
    checkResultWithInlays("class C { void m() { java.util.Collections.addAll(<HINT text=\"c:\"/><caret><Hint text=\",elements:\"/>) } }");
    left();
    type('s');
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { java.util.Collections.addAll(<HINT text=\"c:\"/>s<caret><Hint text=\",elements:\"/>) } }");
    backspace();
    checkResultWithInlays("class C { void m() { java.util.Collections.addAll(<HINT text=\"c:\"/><caret><Hint text=\",elements:\"/>) } }");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { java.util.Collections.addAll(<HINT text=\"c:\"/><caret><Hint text=\",elements:\"/>) } }");
  }

  public void testHintsDontDisappearWhenNavigatingAwayFromUncompletedInvocation() {
    disableVirtualComma();

    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"value:\"/>) } }");
    home();
    type(' ');
    waitForAllAsyncStuff();
    checkResultWithInlays(" <caret>class C { void m() { System.setProperty(<hint text=\"key:\"/>, <hint text=\"value:\"/>) } }");
  }

  public void testHintsDontDisappearOnUnfinishedInputForMethodWithOneParameter() {
    disableVirtualComma();

    configureJava("class C { void m() { System.clear<caret> } }");
    complete();
    checkResultWithInlays("class C { void m() { System.clearProperty(<HINT text=\"key:\"/><caret>) } }");
    home();
    waitForAllAsyncStuff();
    checkResultWithInlays("<caret>class C { void m() { System.clearProperty(<hint text=\"key:\"/>) } }");
  }

  public void testSeveralParametersCompletion() {
    disableVirtualComma();

    configureJava("""
                    class P {
                        void method(int a, int b) {}
                    }
                    class C extends P {
                        void method(int a, int b) {
                            super.meth<caret>
                        }
                    }""");
    complete();
    checkResultWithInlays("""
                            class P {
                                void method(int a, int b) {}
                            }
                            class C extends P {
                                void method(int a, int b) {
                                    super.method(<HINT text="a:"/><caret>, <Hint text="b:"/>);
                                }
                            }""");
    complete("a, b");
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class P {
                                void method(int a, int b) {}
                            }
                            class C extends P {
                                void method(int a, int b) {
                                    super.method(a, b);<caret>
                                }
                            }""");
  }

  public void testLargeNumberOfParameters() {
    disableVirtualComma();

    configureJava("""
                    class C {
                        void mmm(int a, int b, int c, int d, int e, int f) {}
                        void m2() { mm<caret> }
                    }""");
    complete();
    checkResultWithInlays("""
                            class C {
                                void mmm(int a, int b, int c, int d, int e, int f) {}
                                void m2() { mmm(<HINT text="a:"/><caret>, <Hint text="b:"/>, <Hint text="c:"/>, <Hint text="d:"/>, <Hint text="e:"/>, <Hint text="f:"/>); }
                            }""");
  }

  public void testNestedContextIsNotDisposedOnTabbingOutToOuterContext() {
    disableVirtualComma();

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

  public void testHintPopupContentsForMethodWithOverloads() {
    disableVirtualComma();

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
    checkHintContents("""
                        <html><font color=a8a8a8>@NonNls @NotNull String key</font></html>
                        -
                        [<html>@NotNull String key, <b>String def</b></html>]""");
  }

  public void testHintPopupContentsForMethodWithoutOverloads() {
    disableVirtualComma();

    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<HINT text=\"key:\"/><caret>, <Hint text=\"value:\"/>) } }");
    checkHintContents("<html><b>@NonNls @NotNull String</b>&nbsp;&nbsp;<i>the name of the system property.  </i></html>");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<Hint text=\"key:\"/>, <HINT text=\"value:\"/><caret>) } }");
    checkHintContents("<html><b>@NonNls @NotNull String</b>&nbsp;&nbsp;<i>the value of the system property.  </i></html>");
    showParameterInfo();
    waitForAllAsyncStuff();
    checkHintContents("<html>@NonNls @NotNull String key, <b>@NonNls @NotNull String value</b></html>");
  }

  public void testUpInEditor() {
    disableVirtualComma();

    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>@NotNull String</b>&nbsp;&nbsp;<i>the name of the system property.  </i></html>");
    showParameterInfo();
    waitForAllAsyncStuff();
    checkHintContents("""
                        <html><b>@NonNls @NotNull String key</b></html>
                        -
                        [<html><b>@NotNull String key</b>, String def</html>]""");
    up();
    waitForAllAsyncStuff();
    checkResultWithInlays("<caret>class C { void m() { System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>) } }");
    checkHintContents(null);
  }

  public void testDownInEditor() {
    disableVirtualComma();

    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>@NotNull String</b>&nbsp;&nbsp;<i>the name of the system property.  </i></html>");
    showParameterInfo();
    waitForAllAsyncStuff();
    checkHintContents("""
                        <html><b>@NonNls @NotNull String key</b></html>
                        -
                        [<html><b>@NotNull String key</b>, String def</html>]""");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(<hint text=\"key:\"/>, <hint text=\"def:\"/>) } }<caret>");
    checkHintContents(null);
  }

  public void testPopupAfterCaretMovesOutsideOfParenthesis() {
    disableVirtualComma();

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

  public void testNoLineUnderPopupText() {
    disableVirtualComma();

    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key)");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>@NonNls @NotNull String</b>&nbsp;&nbsp;<i>the name of the system property.  </i></html>");
  }

  public void testSwitchIsPossibleForManuallyEnteredUnmatchedMethodCall() {
    disableVirtualComma();

    configureJava("""
                    class C {
                      void a(int p, int q) {}
                      void a(int p, int q, int r) {}
                      void m() { a(<caret>) }
                    }""");
    showParameterInfo();
    checkHintContents("""
                        <html><b>int p</b>, int q</html>
                        -
                        <html><b>int p</b>, int q, int r</html>""");
    methodOverloadDown();
    checkResultWithInlays("""
                            class C {
                              void a(int p, int q) {}
                              void a(int p, int q, int r) {}
                              void m() { a(<HINT text="p:"/><caret>, <Hint text="q:"/>) }
                            }""");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                              void a(int p, int q) {}
                              void a(int p, int q, int r) {}
                              void m() { a(<Hint text="p:"/>, <HINT text="q:"/><caret>) }
                            }""");
  }

  public void testShortHintIsShownAfterFullHint() {
    disableVirtualComma();

    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    waitForAllAsyncStuff();
    showParameterInfo();
    checkHintContents("""
                        <html><b>@NonNls @NotNull String key</b></html>
                        -
                        [<html><b>@NotNull String key</b>, String def</html>]""");
    escape();
    checkHintContents(null);
    right();
    waitForAllAsyncStuff();
    checkHintContents("<html><b>String</b>&nbsp;&nbsp;<i>a default value.  </i></html>");
  }

  public void testAutopopupIsShownWithCompletionHintsDisabled() {
    disableVirtualComma();

    CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = false;
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    checkResultWithInlays("class C { void m() { System.getProperty(<caret>) } }");
    waitForAllAsyncStuff();
    checkHintContents("""
                        <html><b>@NonNls @NotNull String key</b></html>
                        -
                        [<html><b>@NotNull String key</b>, String def</html>]""");
  }

  public void testFullPopupIsHiddenOnTyping() {
    disableVirtualComma();

    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    waitForAutoPopup();
    showParameterInfo();
    type(' ');
    checkHintContents(null);
  }

  public void testFullPopupIsHiddenOnTypingAfterOverloadSwitch() {
    disableVirtualComma();

    configureJava("class C { void m() { System.getProperty(\"a\"<caret>) } }");
    showParameterInfo();
    methodOverloadDown();
    type("\"b");
    checkHintContents(null);
  }

  public void testNextParameterWorksWhenTabCompletionDoesntChangeAnything() {
    disableVirtualComma();

    configureJava("class C { void m() { String local = \"a\"; String local2 = \"b\"; System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    type("local");
    complete();
    assertEquals("local", myFixture.getLookupElements()[0].getLookupString());
    myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE);
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String local = \"a\"; String local2 = \"b\"; System.getProperty(<Hint text=\"key:\"/>local, <HINT text=\"def:\"/><caret>) } }");
  }

  public void testGenericType() {
    disableVirtualComma();

    configureJava("class C { void efgh(Class<?> c) {} void m() { efg<caret> } }");
    complete();
    waitForAllAsyncStuff();
    checkHintContents("<html><b>Class&lt;?&gt;</b></html>");
  }

  public void testCompletionBetweenVarargHints() {
    disableVirtualComma();

    configureJava("class C { int myVal = 1; void vararg(int a, int... b) {} void m() { varar<caret> } }");
    complete();
    checkResultWithInlays("class C { int myVal = 1; void vararg(int a, int... b) {} void m() { vararg(<HINT text=\"a:\"/><caret><Hint text=\",b:\"/>); } }");
    type("myVa");
    complete();
    checkResultWithInlays("class C { int myVal = 1; void vararg(int a, int... b) {} void m() { vararg(<HINT text=\"a:\"/>myVal<caret><Hint text=\",b:\"/>); } }");
  }

  public void testEnteringSpaceBetweenVarargHints() {
    disableVirtualComma();

    configureJava("class C { void vararg(Object a, int... b) {} void m() { varar<caret> } }");
    complete();
    checkResultWithInlays("class C { void vararg(Object a, int... b) {} void m() { vararg(<HINT text=\"a:\"/><caret><Hint text=\",b:\"/>); } }");
    type("new ");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void vararg(Object a, int... b) {} void m() { vararg(<HINT text=\"a:\"/>new <caret><Hint text=\",b:\"/>); } }");
  }

  public void testNoTooltipForInvalidParameter() {
    disableVirtualComma();

    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    type("\"a");
    next();
    type("\"b\",");
    waitForAllAsyncStuff();
    checkHintContents(null);
  }

  public void testIncorrectTooltipIsNotShownForInnerContext() {
    disableVirtualComma();

    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    type("new String(\"");
    waitForAllAsyncStuff();
    checkHintContents(null);
  }

  public void testOverloadsWithOneAndNoParameters() {
    disableVirtualComma();

    configureJava("class C { void method() {} void method(int a) {} void m() { m<caret> } }");
    complete("method(int a)");
    checkResultWithInlays("class C { void method() {} void method(int a) {} void m() { method(<HINT text=\"a:\"/><caret>); } }");
    home();
    waitForAllAsyncStuff();
    checkResultWithInlays("<caret>class C { void method() {} void method(int a) {} void m() { method(); } }");
  }

  public void testCodeFragment() {
    disableVirtualComma();

    PsiExpressionCodeFragment fragment =
      JavaCodeFragmentFactory.getInstance(getProject()).createExpressionCodeFragment("System.getPro<caret>", null, null, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    complete("getProperty(String key, String def)");
    checkResultWithInlays("System.getProperty(<caret>)"); // At the moment, we assure that neither hints, nor comma appear.
                                                          // Later we might make it work correctly for code fragments.
  }

  public void testVarargWithNoMandatoryArgumentsDoesNotKeepHintOnCaretOut() {
    disableVirtualComma();

    configureJava("class C { int vararg(int... args){ return 0; } void m() { varar<caret> } }");
    complete();
    checkResultWithInlays("class C { int vararg(int... args){ return 0; } void m() { vararg(<HINT text=\"args:\"/><caret>) } }");
    home();
    waitForAllAsyncStuff();
    checkResultWithInlays("<caret>class C { int vararg(int... args){ return 0; } void m() { vararg() } }");
  }

  public void testNoLinksInParameterJavadoc() {
    disableVirtualComma();

    configureJava("class C { void m() { String.f<caret> } }");
    complete("format(String format, Object... args)");
    checkResultWithInlays("class C { void m() { String.format(<HINT text=\"format:\"/><caret><Hint text=\",args:\"/>) } }");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>@NotNull String</b>&nbsp;&nbsp;<i>         A format string  </i></html>");
  }

  public void testBasicScenarioForConstructor() {
    disableVirtualComma();

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

  public void testEnumValueOf() {
    disableVirtualComma();

    configureJava("class C { void m() { Thread.State.<caret> } }");
    complete("valueOf(String name)");
    checkResultWithInlays("class C { void m() { Thread.State.valueOf(<HINT text=\"name:\"/><caret>) } }");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>String</b></html>");
  }

  public void testBrokenPsiCall() {
    disableVirtualComma();

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

  public void testKeepHintsLonger() {
    disableVirtualComma();

    RegistryValue setting = Registry.get("editor.keep.completion.hints.longer");
    boolean oldValue = setting.asBoolean();
    try {
      setting.setValue(true);
      configureJava("""
                      class C {
                        void m() { System.setPro<caret> }
                      }""");
      complete("setProperty");
      checkResultWithInlays("""
                              class C {
                                void m() { System.setProperty(<HINT text="key:"/><caret>, <Hint text="value:"/>) }
                              }""");
      type("\"a");
      next();
      type("\"b");
      home();
      waitForAllAsyncStuff();
      checkResultWithInlays("""
                              class C {
                                <caret>void m() { System.setProperty(<hint text="key:"/>"a", <hint text="value:"/>"b") }
                              }""");
      textStart();
      waitForAllAsyncStuff();
      checkResultWithInlays("""
                              <caret>class C {
                                void m() { System.setProperty("a", "b") }
                              }""");
    }
    finally {
      setting.setValue(oldValue);
    }
  }

  public void testOverloadWithNoParameters() {
    disableVirtualComma();

    configureJava("class C { void m() { System.out.pr<caret> } }");
    complete("println(long x)");
    waitForAllAsyncStuff();
    checkHintContents("<html><b>long</b>&nbsp;&nbsp;<i>a The <code>long</code> to be printed.</i></html>");
  }

  public void testSecondCtrlPShowsHints() {
    disableVirtualComma();

    configureJava("class C { void m() { System.setProperty(\"a\", \"<caret>b\"); } }");
    showParameterInfo();
    showParameterInfo();
    checkResultWithInlays("class C { void m() { System.setProperty(<Hint text=\"key:\"/>\"a\", <HINT text=\"value:\"/>\"<caret>b\"); } }");
  }

  public void testQuickDocForOverloadSelectedOnCompletion() {
    disableVirtualComma();

    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key)");
    checkResult("class C { void m() { System.getProperty(<caret>) } }");
    String doc = JavaExternalDocumentationTest.getDocumentationText(getEditor());
    assertTrue(doc.contains("<code>null</code> if there is no property with that key"));
  }

  public void testQuickDocForConstructorOverloadSelectedOnCompletion() {
    configureJava("class C { void m() { new Strin<caret> } }");
    complete("String(byte[] bytes, String charsetName)");
    checkResultWithInlays("class C { void m() { new String(<HINT text=\"bytes:\"/><caret><Hint text=\",charsetName:\"/>) } }");
    String doc = JavaExternalDocumentationTest.getDocumentationText(getEditor());
    assertTrue(doc.contains("If the named charset is not supported"));
  }

  public void testHighlightingOfHintsOnMultipleLines() {
    disableVirtualComma();

    configureJava("class C { void m() { System.setPro<caret> } }");
    complete("setProperty");
    next();
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER);
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.setProperty(<Hint text=\"key:\"/>, \n" +
                          "        <HINT text=\"value:\"/><caret>) } }");
  }

  public void testGlobalStaticMethodCompletion() {
    disableVirtualComma();

    configureJava("class C { void m() { arraycop<caret> } }");
    complete();
    complete();
    assertSize(1, myItems);
    LookupElement item = myItems[0];
    item.as(StaticallyImportable.CLASS_CONDITION_KEY).setShouldBeImported(true); // emulate 'Import statically' intention
    selectItem(item, Lookup.NORMAL_SELECT_CHAR);
    checkResultWithInlays("import static java.lang.System.arraycopy;\n\nclass C { void m() { arraycopy(<HINT text=\"src:\"/><caret>, <Hint text=\"srcPos:\"/>, <Hint text=\"dest:\"/>, <Hint text=\"destPos:\"/>, <Hint text=\"length:\"/>); } }");
  }

  public void testParameterHintsLimit() {
    disableVirtualComma();

    setParameterHintsLimit(2);
    configureJava("""
                    class C {
                        int mmm(int a, int b, int c) { return 0; }
                        void m2() { mm<caret> }
                    }""");
    complete();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<HINT text="a:"/><caret>, <Hint text="b:"/><Hint text="...more:"/>) }
                            }""");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>, <HINT text="b:"/><caret><Hint text="...more:"/>) }
                            }""");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>, <Hint text="b:"/>, <HINT text="c:"/><caret>) }
                            }""");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<hint text="a:"/>, <hint text="b:"/>, <hint text="c:"/>)<caret> }
                            }""");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>, <Hint text="b:"/>, <HINT text="c:"/><caret>) }
                            }""");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>, <HINT text="b:"/><caret><Hint text="...more:"/>) }
                            }""");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<HINT text="a:"/><caret>, <Hint text="b:"/><Hint text="...more:"/>) }
                            }""");
  }

  public void testParameterHintsLimitWithTyping() {
    disableVirtualComma();

    setParameterHintsLimit(2);
    configureJava("""
                    class C {
                        int mmm(int a, int b, int c) { return 0; }
                        void m2() { mm<caret> }
                    }""");
    complete();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<HINT text="a:"/><caret>, <Hint text="b:"/><Hint text="...more:"/>) }
                            }""");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>1, <HINT text="b:"/><caret><Hint text="...more:"/>) }
                            }""");
    type("2");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>1, <Hint text="b:"/>2, <HINT text="c:"/><caret>) }
                            }""");
    type("3");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<hint text="a:"/>1, <hint text="b:"/>2, <hint text="c:"/>3)<caret> }
                            }""");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>1, <Hint text="b:"/>2, <HINT text="c:"/>3<caret>) }
                            }""");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>1, <HINT text="b:"/>2<caret>, <Hint text="c:"/>3) }
                            }""");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c) { return 0; }
                                void m2() { mmm(<HINT text="a:"/>1<caret>, <Hint text="b:"/>2, <Hint text="c:"/>3) }
                            }""");
  }

  public void testParameterHintsLimitMoreParameters() {
    disableVirtualComma();

    setParameterHintsLimit(2);
    configureJava("""
                    class C {
                        int mmm(int a, int b, int c, int d) { return 0; }
                        void m2() { mm<caret> }
                    }""");
    complete();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<HINT text="a:"/><caret>, <Hint text="b:"/><Hint text="...more:"/>) }
                            }""");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>1, <HINT text="b:"/><caret><Hint text="...more:"/>) }
                            }""");
    type("2");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>1, <Hint text="b:"/>2, <HINT text="c:"/><caret><Hint text="...more:"/>) }
                            }""");
    type("3");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>1, <Hint text="b:"/>2, <Hint text="c:"/>3, <HINT text="d:"/><caret>) }
                            }""");
    type("4");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<hint text="a:"/>1, <hint text="b:"/>2, <hint text="c:"/>3, <hint text="d:"/>4)<caret> }
                            }""");
  }

  public void testVarargWithLimit() {
    disableVirtualComma();

    setParameterHintsLimit(1);

    configureJava("class C { void m() { String.f<caret> } }");
    complete("format(String format, Object... args)");
    checkResultWithInlays("class C { void m() { String.format(<HINT text=\"format:\"/><caret><Hint text=\",args:\"/>) } }");
    type("\"a");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<Hint text=\"format:\"/>\"a\", <HINT text=\"args:\"/><caret>) } }");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<hint text=\"format:\"/>\"a\"<hint text=\",args:\"/>)<caret> } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<Hint text=\"format:\"/>\"a\", <HINT text=\"args:\"/><caret>) } }");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { String.format(<HINT text=\"format:\"/>\"a\"<caret><Hint text=\",args:\"/>) } }");
  }

  public void testBlacklistedHintsDoNotAppearWithCompletionHintsDisabled() {
    disableVirtualComma();

    CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = false;
    configureJava("""
                    class C {\s
                      void something() {}
                      void some(int begin) {}
                      void some(int begin, int end) {}
                      void m() { som<caret> }
                    }""");
    complete("some(int begin)");
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {\s
                              void something() {}
                              void some(int begin) {}
                              void some(int begin, int end) {}
                              void m() { some(<caret>); }
                            }""");
    type("0, 1");
    home();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {\s
                              void something() {}
                              void some(int begin) {}
                              void some(int begin, int end) {}
                              <caret>void m() { some(0, 1); }
                            }""");
  }

  public void testVirtualCommaBasicCase() {
    configureJava("class C { int mmm(short a, int b, long c){ return 0; } void m() { mm<caret> } }");
    complete();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<HINT text=\"a:\"/><caret><Hint text=\",b:\"/><Hint text=\",c:\"/>) } }");
    checkHintContents("<html><b>short</b></html>");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/><caret><Hint text=\",c:\"/>) } }");
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

  public void testVirtualCommaEmptyParams() {
    configureJava("class C { int mmm(short a, int b, long c){ return 0; } void m() { mm<caret> } }");
    complete();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<HINT text=\"a:\"/><caret><Hint text=\",b:\"/><Hint text=\",c:\"/>) } }");
    checkHintContents("<html><b>short</b></html>");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<Hint text=\"a:\"/>, <HINT text=\"b:\"/><caret><Hint text=\",c:\"/>) } }");
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
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<Hint text=\"a:\"/>, <HINT text=\"b:\"/><caret><Hint text=\",c:\"/>) } }");
    checkHintContents("<html><b>int</b></html>");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int mmm(short a, int b, long c){ return 0; } void m() { mmm(<HINT text=\"a:\"/><caret><Hint text=\",b:\"/><Hint text=\",c:\"/>) } }");
    checkHintContents("<html><b>short</b></html>");
  }

  public void testVirtualCommaWithLimit() {
    setParameterHintsLimit(2);

    configureJava("""
                    class C {
                        int mmm(int a, int b, int c, int d) { return 0; }
                        void m2() { mm<caret> }
                    }""");
    complete();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<HINT text="a:"/><caret><Hint text=",b:"/><Hint text="...more:"/>) }
                            }""");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>1, <HINT text="b:"/><caret><Hint text="...more:"/>) }
                            }""");
    type("2");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>1, <Hint text="b:"/>2, <HINT text="c:"/><caret><Hint text="...more:"/>) }
                            }""");
    type("3");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>1, <Hint text="b:"/>2, <Hint text="c:"/>3, <HINT text="d:"/><caret>) }
                            }""");
    type("4");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<hint text="a:"/>1, <hint text="b:"/>2, <hint text="c:"/>3, <hint text="d:"/>4)<caret> }
                            }""");
  }

  public void testVirtualCommaWithLimitEmptyParams() {
    setParameterHintsLimit(2);

    configureJava("""
                    class C {
                        int mmm(int a, int b, int c, int d) { return 0; }
                        void m2() { mm<caret> }
                    }""");
    complete();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<HINT text="a:"/><caret><Hint text=",b:"/><Hint text="...more:"/>) }
                            }""");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>, <HINT text="b:"/><caret><Hint text="...more:"/>) }
                            }""");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>, <Hint text="b:"/>, <HINT text="c:"/><caret><Hint text="...more:"/>) }
                            }""");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>, <Hint text="b:"/>, <Hint text="c:"/>, <HINT text="d:"/><caret>) }
                            }""");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<hint text="a:"/>, <hint text="b:"/>, <hint text="c:"/>, <hint text="d:"/>)<caret> }
                            }""");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>, <Hint text="b:"/>, <Hint text="c:"/>, <HINT text="d:"/><caret>) }
                            }""");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>, <Hint text="b:"/>, <HINT text="c:"/><caret><Hint text="...more:"/>) }
                            }""");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>, <HINT text="b:"/><caret><Hint text="...more:"/>) }
                            }""");
    prev();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<HINT text="a:"/><caret><Hint text=",b:"/><Hint text="...more:"/>) }
                            }""");
  }

  public void testVirtualCommaWithOverload() {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    checkResultWithInlays("class C { void m() { System.getProperty(<HINT text=\"key:\"/><caret><Hint text=\",def:\"/>) } }");
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

  public void testVirtualCommaWithManyParams() {
    configureJava("""
                    class C {
                        int mmm(int a, int b, int c, int d) { return 0; }
                        void m2() { mm<caret> }
                    }""");
    complete();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<HINT text="a:"/><caret><Hint text=",b:"/><Hint text=",c:"/><Hint text=",d:"/>) }
                            }""");
    type("1");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>1, <HINT text="b:"/><caret><Hint text=",c:"/><Hint text=",d:"/>) }
                            }""");
    type("2");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>1, <Hint text="b:"/>2, <HINT text="c:"/><caret><Hint text=",d:"/>) }
                            }""");
    type("3");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<Hint text="a:"/>1, <Hint text="b:"/>2, <Hint text="c:"/>3, <HINT text="d:"/><caret>) }
                            }""");
    type("4");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                                int mmm(int a, int b, int c, int d) { return 0; }
                                void m2() { mmm(<hint text="a:"/>1, <hint text="b:"/>2, <hint text="c:"/>3, <hint text="d:"/>4)<caret> }
                            }""");
  }

  public void testCaretMovementOverVirtualComma() {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    checkResultWithInlays("class C { void m() { System.getProperty(<HINT text=\"key:\"/><caret><Hint text=\",def:\"/>) } }");

    right();
    checkResultWithInlays("class C { void m() { System.getProperty(<Hint text=\"key:\"/>, <HINT text=\"def:\"/><caret>) } }");
  }

  public void testHintsAreNotShownInImproperContext() {
    disableVirtualComma();

    configureJava("class C { void m() { int a = Math.ma<caret>5; } }");
    complete("max(int a, int b)");
    checkResultWithInlays("class C { void m() { int a = Math.max(<caret>)5; } }");
  }

  public void testConstructorInvocationInsideMethodInvocation() {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key)");
    checkResultWithInlays("class C { void m() { System.getProperty(<caret>) } }");
    type("new Strin");
    complete("String(byte[] bytes, String charsetName)");
    checkResultWithInlays("class C { void m() { System.getProperty(new String(<HINT text=\"bytes:\"/><caret><Hint text=\",charsetName:\"/>)) } }");
    type("null");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { System.getProperty(new String(<Hint text=\"bytes:\"/>null, <HINT text=\"charsetName:\"/><caret>)) } }");
  }

  public void testFieldAccessInsideMethodInvocation() {
    configureJava("""
                    class C {
                      int x;
                      void some(int a, int b) {}
                      void other() { som<caret> }
                    }""");
    complete();
    checkResultWithInlays("""
                            class C {
                              int x;
                              void some(int a, int b) {}
                              void other() { some(<HINT text="a:"/><caret><Hint text=",b:"/>); }
                            }""");
    type("this.");
    complete("x");
    checkResultWithInlays("""
                            class C {
                              int x;
                              void some(int a, int b) {}
                              void other() { some(<HINT text="a:"/>this.x<caret><Hint text=",b:"/>); }
                            }""");
    next();
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                              int x;
                              void some(int a, int b) {}
                              void other() { some(<Hint text="a:"/>this.x, <HINT text="b:"/><caret>); }
                            }""");
  }

  public void testParameterPopupAfterManuallyRenamingOneOverload() {
    disableVirtualComma();

    configureJava("""
                    class C {
                      void some(int a) {}
                      void some(int a, int b) {}
                      void other() { s<caret> }
                    }""");
    complete("some(int a)");
    checkResultWithInlays("""
                            class C {
                              void some(int a) {}
                              void some(int a, int b) {}
                              void other() { some(<HINT text="a:"/><caret>); }
                            }""");

    mouse().clickAt(2, 11);
    type('2');
    mouse().clickAt(3, 23);
    waitForAllAsyncStuff();
    checkResultWithInlays("""
                            class C {
                              void some(int a) {}
                              void some2(int a, int b) {}
                              void other() { some(<HINT text="a:"/><caret>); }
                            }""");

    showParameterInfo();
    checkHintContents("<html><b>int a</b></html>");
  }

  public void testOverloadSwitchToMoreParametersWithVirtualComma() {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key)");
    showParameterInfo();
    methodOverloadDown();
    checkResultWithInlays("class C { void m() { System.getProperty(<HINT text=\"key:\"/><caret><Hint text=\",def:\"/>) } }");
  }

  public void testOverloadSwitchToLessParametersWithVirtualComma() {
    configureJava("class C { void m() { Character.to<caret> } }");
    complete("toChars(int codePoint, char[] dst, int dstIndex)");
    waitForAutoPopup();
    showParameterInfo();
    methodOverloadUp();
    checkResultWithInlays("class C { void m() { Character.toChars(<HINT text=\"codePoint:\"/><caret>) } }");
  }

  public void testHintsDontDisappearOnIncompleteCallWithVirtualComma() {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    checkResultWithInlays("class C { void m() { System.getProperty(<HINT text=\"key:\"/><caret><Hint text=\",def:\"/>) } }");
    home();
    type(' ');
    waitForAllAsyncStuff();
    checkResultWithInlays(" <caret>class C { void m() { System.getProperty(<hint text=\"key:\"/><hint text=\",def:\"/>) } }");
  }

  public void testCodeFragmentWithVirtualComma() {
    PsiExpressionCodeFragment fragment =
      JavaCodeFragmentFactory.getInstance(getProject()).createExpressionCodeFragment("System.getPro<caret>", null, null, true);
    myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
    complete("getProperty(String key, String def)");
    checkResultWithInlays("System.getProperty(<caret>)"); // At the moment, we assure that neither hints, nor comma appear.
                                                          // Later we might make it work correctly for code fragments.
  }

  public void testAutoCompletionOfOverloadedMethod() {
    configureJava("""
                    class C {
                      void some(int a, int b) {}
                      void some(int c, int d, int e) {}  void m() { som<caret> }
                    }""");
    complete();
    checkResultWithInlays("""
                            class C {
                              void some(int a, int b) {}
                              void some(int c, int d, int e) {}  void m() { some(<caret>); }
                            }""");
    waitForAllAsyncStuff();
    checkHintContents("""
                        [<html><b>int a</b>, int b</html>]
                        -
                        <html><b>int c</b>, int d, int e</html>""");
  }

  public void testUndoRedoAfterOverloadSwitch() {
    configureJava("class C { void some(int a) {} void some(int a, int b) {} void m() { s<caret> } }");
    complete("some(int a)");
    type('1');
    waitForAutoPopup();
    showParameterInfo();
    methodOverloadDown();
    checkResultWithInlays("class C { void some(int a) {} void some(int a, int b) {} void m() { some(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/><caret>); } }");
    EditorTestUtil.testUndoInEditor(getEditor(), () -> {
      myFixture.performEditorAction(IdeActions.ACTION_UNDO);
      waitForAllAsyncStuff();
      myFixture.performEditorAction(IdeActions.ACTION_REDO);
      waitForAllAsyncStuff();
    });
    checkResultWithInlays("class C { void some(int a) {} void some(int a, int b) {} void m() { some(<Hint text=\"a:\"/>1, <HINT text=\"b:\"/><caret>); } }");
  }

  public void testCommaAfterSmartCompletionOfOverloadedMethodParameter() {
    configureJava("class C { int codePoint = 123; void m() { Character.to<caret> } }");
    complete("toChars(int codePoint, char[] dst, int dstIndex)");
    type("codePoin");
    completeSmart("codePoint");
    checkResultWithInlays("class C { int codePoint = 123; void m() { Character.toChars(<HINT text=\"codePoint:\"/>codePoint, <hint text=\"dst:\"/><caret><Hint text=\",dstIndex:\"/>) } }");
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { int codePoint = 123; void m() { Character.toChars(<Hint text=\"codePoint:\"/>codePoint, <HINT text=\"dst:\"/><caret><Hint text=\",dstIndex:\"/>) } }");
  }

  public void testOverloadSwitchingForVarargMethod() {
    configureJava("class C { void some(int a) {} void some(int a, int... b) {} void m() { s<caret> } }");
    complete("some(int a, int... b)");
    type('1');
    next();
    type('2');
    next();
    type('3');
    waitForAutoPopup();
    showParameterInfo();
    methodOverloadUp();
    checkResultWithInlays("class C { void some(int a) {} void some(int a, int... b) {} void m() { some(<HINT text=\"a:\"/>1<caret>); } }");
  }

  public void testHideHintsForIncompleteCallIfOverloadMatches() {
    configureJava("class C { void m() { System.getPro<caret> } }");
    complete("getProperty(String key, String def)");
    type("\"a");
    home();
    waitForAllAsyncStuff();
    checkResultWithInlays("<caret>class C { void m() { System.getProperty(\"a\") } }");
  }

  public void testConstructorInSmartCompletion() {
    configureJava("class C { void m() { throw new <caret> } }");
    completeSmart("Error(String message, Throwable cause)");
    checkResultWithInlays("class C { void m() { throw new Error(<HINT text=\"message:\"/><caret><Hint text=\",cause:\"/>); } }");
  }

  public void testSuggestConstructorsForNonImportedTypeAfterNew() {
    configureJava("class C { new LinkedHashMa<caret>x } }");
    complete();
    PsiMethod[] constructors = myFixture.findClass(LinkedHashMap.class.getName()).getConstructors();
    assertSize(constructors.length, myItems);
    for (LookupElement item : myItems) {
      assertInstanceOf(item.getPsiElement(), PsiMethod.class);
    }
  }

  public void testCommasAutoInsertOnNextWord() {
    configureJava("class C { void m() { Character.to<caret> } }");
    complete("toChars(int codePoint, char[] dst, int dstIndex)");
    checkResultWithInlays("class C { void m() { Character.toChars(<HINT text=\"codePoint:\"/><caret><Hint text=\",dst:\"/><Hint text=\",dstIndex:\"/>) } }");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_NEXT_WORD);
    waitForAllAsyncStuff();
    checkResultWithInlays(
      "class C { void m() { Character.toChars(<hint text=\"codePoint:\"/><hint text=\",dst:\"/><hint text=\",dstIndex:\"/>)<caret> } }");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD);
    waitForAllAsyncStuff();
    checkResultWithInlays("class C { void m() { Character.toChars(<Hint text=\"codePoint:\"/>, <Hint text=\"dst:\"/>, <HINT text=\"dstIndex:\"/><caret>) } }");
  }

  public void testPreferSamePackageClassesToConstructorsFromNonImportedClass() {
    myFixture.addClass("package pkg; public class SubmissionPublisher {" +
                       "public SubmissionPublisher(int a) {}" +
                       "public SubmissionPublisher(int a, int b) {}" +
                       "}");
    myFixture.addClass("class Submarine {}");
    configureJava("class C { new Subm<caret> } }");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "Submarine", "SubmissionPublisher");
  }

  private void checkResultWithInlays(String text) {
    waitForParameterInfo();
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
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

  private void methodOverloadUp() {
    myFixture.performEditorAction(IdeActions.ACTION_METHOD_OVERLOAD_SWITCH_UP);
    waitForParameterInfo();
  }

  private void methodOverloadDown() {
    myFixture.performEditorAction(IdeActions.ACTION_METHOD_OVERLOAD_SWITCH_DOWN);
    waitForParameterInfo();
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

  private EditorMouseFixture mouse() {
    return new EditorMouseFixture((EditorImpl)getEditor());
  }

  private void setParameterHintsLimit(int limit) {
    Registry.get("editor.completion.hints.per.call.limit").setValue(limit, getTestRootDisposable());
  }

  private void disableVirtualComma() {
    Registry.get("editor.completion.hints.virtual.comma").setValue(false, getTestRootDisposable());
  }
}