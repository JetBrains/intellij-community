// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.java.codeInsight.ParameterInfoFixture;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.ParameterInfoFixtureKt.parameterInfoFixture;

@TestApplication
public class EditorTabOutTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<ParameterInfoFixture> parameterInfo = parameterInfoFixture(projectFixture);
  private boolean mySavedTabOutSetting;

  @BeforeEach
  void setUp() {
    mySavedTabOutSetting = CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES;
    CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES = true;
  }

  @AfterEach
  void tearDown() {
    CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES = mySavedTabOutSetting;
  }

  @Test
  public void testMethodCallAndStringLiteral() {
    ParameterInfoFixture info = parameterInfo.get();
    info.configureJava("class C { void m() { System.geten<caret> } }");
    EdtTestUtil.runInEdtAndWait(() -> {
      info.complete();
      info.type("\"a");
      info.checkResult("class C { void m() { System.getenv(\"a<caret>\") } }");
      tabOut();
      info.checkResult("class C { void m() { System.getenv(\"a\"<caret>) } }");
      tabOut();
      info.checkResult("class C { void m() { System.getenv(\"a\")<caret> } }");
      left();
      left();
      tabOut();
      info.checkResult("class C { void m() { System.getenv(\"a<caret>\") } }");
      right();
      tabOut();
      info.checkResult("class C { void m() { System.getenv(\"a\"<caret>) } }");
    });
  }

  @Test
  public void testMultiCaret() {
    ParameterInfoFixture info = parameterInfo.get();
    info.configureJava("class C { void m() { System.out.println(<caret>); System.out.println(<caret>); } }");
    EdtTestUtil.runInEdtAndWait(() -> {
      info.type("\"a");
      tabOut();
      info.checkResult("class C { void m() { System.out.println(\"a\"<caret>); System.out.println(\"a\"<caret>); } }");
    });
  }

  @Test
  public void testGeneric() {
    ParameterInfoFixture info = parameterInfo.get();
    info.configureJava("class C { Comparable<caret> }");
    EdtTestUtil.runInEdtAndWait(() -> {
      info.type("<String");
      tabOut();
      info.checkResult("class C { Comparable<String><caret> }");
    });
  }

  @Test
  public void testArray() {
    ParameterInfoFixture info = parameterInfo.get();
    info.configureJava("class C { int[] ar = new in<caret> }");
    EdtTestUtil.runInEdtAndWait(() -> {
      info.complete();
      info.type("123");
      tabOut();
      info.checkResult("class C { int[] ar = new int[123]<caret> }");
    });
  }

  @Test
  public void testWhile() {
    ParameterInfoFixture info = parameterInfo.get();
    info.configureJava("class C { void m() { whil<caret> } }");
    EdtTestUtil.runInEdtAndWait(() -> {
      info.complete();
      info.type("true");
      tabOut();
      info.checkResult("class C { void m() { while (true)<caret>} }");
    });
  }

  @Test
  public void testAddImport() {
    ParameterInfoFixture info = parameterInfo.get();
    info.configureJava("""
                    class C {
                      java.util.List<caret>
                    }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      info.type("<ArrayList");
      runImportClassIntention();
      tabOut();
      info.checkResult("""
                  import java.util.ArrayList;

                  class C {
                    java.util.List<ArrayList><caret>
                  }""");
    });
  }

  @Test
  public void testSemicolon() {
    ParameterInfoFixture info = parameterInfo.get();
    info.configureJava("class C { void m() { System.exi<caret> } }");
    EdtTestUtil.runInEdtAndWait(() -> {
      info.complete("exit");
      info.checkResult("class C { void m() { System.exit(<caret>); } }");
      info.type('1');
      tabOut();
      info.checkResult("class C { void m() { System.exit(1);<caret> } }");
    });
  }

  @Test
  public void testScopeRange() {
    ParameterInfoFixture info = parameterInfo.get();
    info.configureJava("class C { static { new HashM<caret> }}");
    EdtTestUtil.runInEdtAndWait(() -> {
      info.complete("HashMap");
      info.checkResult("import java.util.HashMap;\n\nclass C { static { new HashMap<<caret>>()\n}}");
      tabOut();
      info.checkResult("import java.util.HashMap;\n\nclass C { static { new HashMap<>(<caret>)\n}}");
      tabOut();
      info.checkResult("import java.util.HashMap;\n\nclass C { static { new HashMap<>()<caret>\n}}");
    });
  }

  @Test
  public void testScopeRangeInjected() {
    ParameterInfoFixture info = parameterInfo.get();
    info.configureJava("import org.intellij.lang.annotations.Language;" +
                  "class Main { " +
                  "  static {" +
                  "    injected(\"class C { static { new HashM<caret> }}\");" +
                  "  } " +
                  "  static void injected(@Language(\"JAVA\") String value){}" +
                  "}");
    EdtTestUtil.runInEdtAndWait(() -> {
      info.complete("HashMap");
      info.checkResult("import org.intellij.lang.annotations.Language;" +
                  "class Main { " +
                  "  static {" +
                  "    injected(\"import java.util.HashMap;class C { static { new HashMap<<caret>>() }}\");" +
                  "  } " +
                  "  static void injected(@Language(\"JAVA\") String value){}" +
                  "}");
      tabOut();
      info.checkResult("import org.intellij.lang.annotations.Language;" +
                  "class Main { " +
                  "  static {" +
                  "    injected(\"import java.util.HashMap;class C { static { new HashMap<>(<caret>) }}\");" +
                  "  } " +
                  "  static void injected(@Language(\"JAVA\") String value){}" +
                  "}"
      );
      tabOut();
      info.checkResult("import org.intellij.lang.annotations.Language;" +
                  "class Main { " +
                  "  static {" +
                  "    injected(\"import java.util.HashMap;class C { static { new HashMap<>()<caret> }}\");" +
                  "  } " +
                  "  static void injected(@Language(\"JAVA\") String value){}" +
                  "}"
      );
    });
  }

  @Test
  public void testAnonymousClass() {
    ParameterInfoFixture info = parameterInfo.get();
    info.getFixture().addClass("package p; public class ABC<T> {}");
    info.configureJava("""
                    class Main {
                      static {
                        new Runnable() {
                          {
                          A<caret>
                          }
                        };
                      }
                    }""");

    EdtTestUtil.runInEdtAndWait(() -> {
      final LookupElement[] elements = info.getFixture().completeBasic();
      final LookupElement element = ParameterInfoFixture.findLookupElementContainingText(elements, "ABC");

      final LookupImpl lookup = info.getLookup();
      lookup.finishLookup('<', element);

      ParameterInfoFixture.waitForParameterInfo();
      info.checkResult("""
                  import p.ABC;

                  class Main {
                    static {
                      new Runnable() {
                        {
                            ABC<<caret>>
                        }
                      };
                    }
                  }""");
      tabOut();
      info.checkResult("""
                  import p.ABC;

                  class Main {
                    static {
                      new Runnable() {
                        {
                            ABC<><caret>
                        }
                      };
                    }
                  }""");
    });
  }

  private void tabOut() {
    parameterInfo.get().getFixture().performEditorAction(IdeActions.ACTION_BRACE_OR_QUOTE_OUT);
  }

  private void left() {
    parameterInfo.get().getFixture().performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT);
  }

  private void right() {
    parameterInfo.get().getFixture().performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
  }

  private void runImportClassIntention() {
    parameterInfo.get().getFixture().launchAction(parameterInfo.get().getFixture().findSingleIntention("Import class"));
  }
}
