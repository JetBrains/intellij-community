// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.java.codeInsight.JavaDocumentationTestUtil;
import com.intellij.java.codeInsight.ParameterInfoFixture;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiMethod;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.AutoPopupParameterInfoTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.ParameterInfoFixtureKt.parameterInfoFixture;
import static com.intellij.testFramework.UsefulTestCase.assertInstanceOf;
import static com.intellij.testFramework.UsefulTestCase.assertSize;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NeedsIndex.SmartMode(reason = "Hints shouldn't work in dumb mode")
@TestApplication
@IndexingModeTemplate
public class CompletionHintsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<ParameterInfoFixture> parameterInfo = parameterInfoFixture(projectFixture);
  private ParameterInfoFixture info;
  private LightJavaCodeInsightTestFixture myFixture;
  private boolean myStoredParamHintsValue;
  private boolean myStoredTabOutValue;

  @BeforeEach
  void setUp() {
    info = parameterInfo.get();
    myFixture = info.getFixture();
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    myStoredParamHintsValue = settings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION;
    myStoredTabOutValue = settings.TAB_EXITS_BRACKETS_AND_QUOTES;
    settings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = true;
    settings.TAB_EXITS_BRACKETS_AND_QUOTES = false;
  }

  @AfterEach
  void tearDown() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    settings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = myStoredParamHintsValue;
    settings.TAB_EXITS_BRACKETS_AND_QUOTES = myStoredTabOutValue;
  }

  @Test
  public void testQuickDocForConstructorOverloadSelectedOnCompletion() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureJava("class C { void m() { new Strin<caret> } }");
      complete("String(byte[] bytes, String charsetName)");
      checkResultWithInlays("class C { void m() { new String(<HINT text=\"bytes:\"/><caret><Hint text=\",charsetName:\"/>) } }");
      String doc = JavaDocumentationTestUtil.getDocumentationText(getEditor());
      assertTrue(doc.contains("If the named charset is not supported"));
    });
  }

  @Test
  public void testVirtualCommaBasicCase() {
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testVirtualCommaEmptyParams() {
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testVirtualCommaWithLimit() {
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testVirtualCommaWithLimitEmptyParams() {
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testVirtualCommaWithOverload() {
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testVirtualCommaWithManyParams() {
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testCaretMovementOverVirtualComma() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureJava("class C { void m() { System.getPro<caret> } }");
      complete("getProperty(String key, String def)");
      checkResultWithInlays("class C { void m() { System.getProperty(<HINT text=\"key:\"/><caret><Hint text=\",def:\"/>) } }");

      right();
      checkResultWithInlays("class C { void m() { System.getProperty(<Hint text=\"key:\"/>, <HINT text=\"def:\"/><caret>) } }");
    });
  }

  @Test
  public void testConstructorInvocationInsideMethodInvocation() {
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testFieldAccessInsideMethodInvocation() {
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testOverloadSwitchToMoreParametersWithVirtualComma() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureJava("class C { void m() { System.getPro<caret> } }");
      complete("getProperty(String key)");
      showParameterInfo();
      methodOverloadDown();
      checkResultWithInlays("class C { void m() { System.getProperty(<HINT text=\"key:\"/><caret><Hint text=\",def:\"/>) } }");
    });
  }

  @Test
  public void testOverloadSwitchToLessParametersWithVirtualComma() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureJava("class C { void m() { Character.to<caret> } }");
      complete("toChars(int codePoint, char[] dst, int dstIndex)");
      AutoPopupParameterInfoTestUtil.waitForAutoPopup(myFixture.getProject());
      showParameterInfo();
      methodOverloadUp();
      checkResultWithInlays("class C { void m() { Character.toChars(<HINT text=\"codePoint:\"/><caret>) } }");
    });
  }

  @Test
  public void testHintsDontDisappearOnIncompleteCallWithVirtualComma() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureJava("class C { void m() { System.getPro<caret> } }");
      complete("getProperty(String key, String def)");
      checkResultWithInlays("class C { void m() { System.getProperty(<HINT text=\"key:\"/><caret><Hint text=\",def:\"/>) } }");
      home();
      type(' ');
      waitForAllAsyncStuff();
      checkResultWithInlays(" <caret>class C { void m() { System.getProperty(<hint text=\"key:\"/><hint text=\",def:\"/>) } }");
    });
  }

  @Test
  public void testCodeFragmentWithVirtualComma() {
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiExpressionCodeFragment fragment =
        JavaCodeFragmentFactory.getInstance(getProject()).createExpressionCodeFragment("System.getPro<caret>", null, null, true);
      myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
      complete("getProperty(String key, String def)");
      checkResultWithInlays("System.getProperty(<caret>)"); // At the moment, we assure that neither hints, nor comma appear.
                                                            // Later we might make it work correctly for code fragments.
    });
  }

  @Test
  public void testAutoCompletionOfOverloadedMethod() {
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testUndoRedoAfterOverloadSwitch() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureJava("class C { void some(int a) {} void some(int a, int b) {} void m() { s<caret> } }");
      complete("some(int a)");
      type('1');
      AutoPopupParameterInfoTestUtil.waitForAutoPopup(myFixture.getProject());
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
    });
  }

  @Test
  public void testCommaAfterSmartCompletionOfOverloadedMethodParameter() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureJava("class C { int codePoint = 123; void m() { Character.to<caret> } }");
      complete("toChars(int codePoint, char[] dst, int dstIndex)");
      type("codePoin");
      completeSmart("codePoint");
      checkResultWithInlays("class C { int codePoint = 123; void m() { Character.toChars(<Hint text=\"codePoint:\"/>codePoint, <HINT text=\"dst:\"/><caret><Hint text=\",dstIndex:\"/>) } }");
      waitForAllAsyncStuff();
      checkResultWithInlays("class C { int codePoint = 123; void m() { Character.toChars(<Hint text=\"codePoint:\"/>codePoint, <HINT text=\"dst:\"/><caret><Hint text=\",dstIndex:\"/>) } }");
    });
  }

  @Test
  public void testHintsUpdatedAfterSmartCompletionOfVariableArgument() {
    EdtTestUtil.runInEdtAndWait(() -> {
      doTestHintsAfterSmartCompletionOfArgument("cls");
    });
  }

  @Test
  public void testHintsUpdatedAfterSmartCompletionOfClassLiteralArgument() {
    EdtTestUtil.runInEdtAndWait(() -> {
      doTestHintsAfterSmartCompletionOfArgument("String.class");
    });
  }

  private void doTestHintsAfterSmartCompletionOfArgument(String argument) {
    configureJava("class C { Class<String> cls; void accept(Class<String> first, int second) {} void m() { acc<caret> } }");
    complete();
    waitForParameterInfo();
    completeSmart(argument);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultWithInlays("class C { Class<String> cls; void accept(Class<String> first, int second) {} void m() { accept(<Hint text=\"first:\"/>" +
                          argument + ", <HINT text=\"second:\"/><caret>); } }");
  }

  @Test
  public void testOverloadSwitchingForVarargMethod() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureJava("class C { void some(int a) {} void some(int a, int... b) {} void m() { s<caret> } }");
      complete("some(int a, int... b)");
      type('1');
      next();
      type('2');
      next();
      type('3');
      AutoPopupParameterInfoTestUtil.waitForAutoPopup(myFixture.getProject());
      showParameterInfo();
      methodOverloadUp();
      checkResultWithInlays("class C { void some(int a) {} void some(int a, int... b) {} void m() { some(<HINT text=\"a:\"/>1<caret>); } }");
    });
  }

  @Test
  public void testHideHintsForIncompleteCallIfOverloadMatches() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureJava("class C { void m() { System.getPro<caret> } }");
      complete("getProperty(String key, String def)");
      type("\"a");
      home();
      waitForAllAsyncStuff();
      checkResultWithInlays("<caret>class C { void m() { System.getProperty(\"a\") } }");
    });
  }

  @Test
  public void testConstructorInSmartCompletion() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureJava("class C { void m() { throw new <caret> } }");
      completeSmart("Error(String message, Throwable cause)");
      checkResultWithInlays("class C { void m() { throw new Error(<HINT text=\"message:\"/><caret><Hint text=\",cause:\"/>); } }");
    });
  }

  @Test
  public void testSuggestConstructorsForNonImportedTypeAfterNew() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureJava("class C { new LinkedHashMa<caret>x } }");
      complete();
      PsiMethod[] constructors = myFixture.findClass(LinkedHashMap.class.getName()).getConstructors();
      assertSize(constructors.length, info.getItems());
      for (LookupElement item : info.getItems()) {
        assertInstanceOf(item.getPsiElement(), PsiMethod.class);
      }
    });
  }

  @Test
  public void testCommasAutoInsertOnNextWord() {
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testPreferSamePackageClassesToConstructorsFromNonImportedClass() {
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package pkg; public class SubmissionPublisher {" +
                         "public SubmissionPublisher(int a) {}" +
                         "public SubmissionPublisher(int a, int b) {}" +
                         "}");
      myFixture.addClass("class Submarine {}");
      configureJava("class C { new Subm<caret> } }");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "Submarine", "SubmissionPublisher");
    });
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

  private void right() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
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

  private void setParameterHintsLimit(int limit) {
    Registry.get("editor.completion.hints.per.call.limit").setValue(limit, myFixture.getTestRootDisposable());
  }

  private Editor getEditor() {
    return myFixture.getEditor();
  }

  private Project getProject() {
    return myFixture.getProject();
  }

  private void configureJava(String text) {
    info.configureJava(text);
  }

  private void complete() {
    info.complete();
  }

  private void complete(String partOfItemText) {
    info.complete(partOfItemText);
  }

  private void completeSmart(String partOfItemText) {
    info.completeSmart(partOfItemText);
  }

  private void type(String s) {
    info.type(s);
  }

  private void type(char c) {
    info.type(c);
  }

  private void showParameterInfo() {
    info.showParameterInfo();
  }

  private void checkHintContents(String hintText) {
    info.checkHintContents(hintText);
  }

  private void waitForAllAsyncStuff() {
    info.waitForAllAsyncStuff();
  }

  private static void waitForParameterInfo() {
    ParameterInfoFixture.waitForParameterInfo();
  }
}
