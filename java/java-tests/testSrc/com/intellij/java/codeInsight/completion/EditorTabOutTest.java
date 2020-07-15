// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.java.codeInsight.AbstractParameterInfoTestCase;
import com.intellij.openapi.actionSystem.IdeActions;

public class EditorTabOutTest extends AbstractParameterInfoTestCase {
  private boolean mySavedTabOutSetting;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySavedTabOutSetting = CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES;
    CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES = true;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES = mySavedTabOutSetting;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testMethodCallAndStringLiteral() {
    configureJava("class C { void m() { System.geten<caret> } }");
    complete();
    type("\"a");
    checkResult("class C { void m() { System.getenv(\"a<caret>\") } }");
    tabOut();
    checkResult("class C { void m() { System.getenv(\"a\"<caret>) } }");
    tabOut();
    checkResult("class C { void m() { System.getenv(\"a\")<caret> } }");
    left();
    left();
    tabOut();
    checkResult("class C { void m() { System.getenv(\"a<caret>\") } }");
    right();
    tabOut();
    checkResult("class C { void m() { System.getenv(\"a\"<caret>) } }");
  }

  public void testMultiCaret() {
    configureJava("class C { void m() { System.out.println(<caret>); System.out.println(<caret>); } }");
    type("\"a");
    tabOut();
    checkResult("class C { void m() { System.out.println(\"a\"<caret>); System.out.println(\"a\"<caret>); } }");
  }

  public void testGeneric() {
    configureJava("class C { Comparable<caret> }");
    type("<String");
    tabOut();
    checkResult("class C { Comparable<String><caret> }");
  }

  public void testArray() {
    configureJava("class C { int[] ar = new in<caret> }");
    complete();
    type("123");
    tabOut();
    checkResult("class C { int[] ar = new int[123]<caret> }");
  }

  public void testWhile() {
    configureJava("class C { void m() { whil<caret> } }");
    complete();
    type("true");
    tabOut();
    checkResult("class C { void m() { while (true)<caret>} }");
  }

  public void testAddImport() {
    configureJava("class C {\n" +
                  "  java.util.List<caret>\n" +
                  "}");
    type("<ArrayList");
    runImportClassIntention();
    tabOut();
    checkResult("import java.util.ArrayList;\n" +
                "\n" +
                "class C {\n" +
                "  java.util.List<ArrayList><caret>\n" +
                "}");
  }

  public void testSemicolon() {
    configureJava("class C { void m() { System.exi<caret> } }");
    complete("exit");
    checkResult("class C { void m() { System.exit(<caret>); } }");
    type('1');
    tabOut();
    checkResult("class C { void m() { System.exit(1);<caret> } }");
  }

  public void testScopeRange() {
    configureJava("class C { static { new HashM<caret> }}");
    complete("HashMap");
    checkResult("import java.util.HashMap;\n\nclass C { static { new HashMap<<caret>>()\n}}");
    tabOut();
    checkResult("import java.util.HashMap;\n\nclass C { static { new HashMap<>(<caret>)\n}}");
    tabOut();
    checkResult("import java.util.HashMap;\n\nclass C { static { new HashMap<>()<caret>\n}}");
  }

  public void testScopeRangeInjected() {
    configureJava("import org.intellij.lang.annotations.Language;" +
                  "class Main { " +
                  "  static {" +
                  "    injected(\"class C { static { new HashM<caret> }}\");" +
                  "  } " +
                  "  static void injected(@Language(\"JAVA\") String value){}" +
                  "}");
    complete("HashMap");
    checkResult("import org.intellij.lang.annotations.Language;" +
                "class Main { " +
                "  static {" +
                "    injected(\"import java.util.HashMap;class C { static { new HashMap<<caret>>() }}\");" +
                "  } " +
                "  static void injected(@Language(\"JAVA\") String value){}" +
                "}");
    tabOut();
    checkResult("import org.intellij.lang.annotations.Language;" +
                "class Main { " +
                "  static {" +
                "    injected(\"import java.util.HashMap;class C { static { new HashMap<>(<caret>) }}\");" +
                "  } " +
                "  static void injected(@Language(\"JAVA\") String value){}" +
                "}"
    );
    tabOut();
    checkResult("import org.intellij.lang.annotations.Language;" +
                "class Main { " +
                "  static {" +
                "    injected(\"import java.util.HashMap;class C { static { new HashMap<>()<caret> }}\");" +
                "  } " +
                "  static void injected(@Language(\"JAVA\") String value){}" +
                "}"
    );
  }

  private void tabOut() {
    myFixture.performEditorAction(IdeActions.ACTION_BRACE_OR_QUOTE_OUT);
  }

  private void left() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT);
  }

  private void right() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
  }

  private void runImportClassIntention() {
    myFixture.launchAction(myFixture.findSingleIntention("Import class"));
  }
}
