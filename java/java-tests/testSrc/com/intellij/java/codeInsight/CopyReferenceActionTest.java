// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jdom.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class CopyReferenceActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private Element mySavedSettings;

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/copyReference");
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    mySavedSettings = settings.getState();
    settings.ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES;
  }

  @AfterEach
  void tearDown() {
    if (mySavedSettings != null) {
      CodeInsightSettings.getInstance().loadState(mySavedSettings);
    }
  }

  @Test
  public void testConstructor() { doTest(); }
  @Test
  public void testDefaultConstructor() { doTest(); }
  @Test
  public void testIdentifierSeparator() { doTest(); }
  @Test
  public void testMethodFromAnonymousClass() { doTest(); }

  @Test
  public void testSameClassNames() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package p; public class Foo {}");
    myFixture.configureByText("Foo.java", "package p1; public class Fo<caret>o {}");
    performCopy();
    myFixture.configureByText("a.java", "import p.Foo; class Bar extends Foo { <caret>}");
    performPaste();
    myFixture.checkResult("import p.Foo; class Bar extends Foo { p1.Foo}");
  }

  @Test
  public void testAddImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public class Foo {}");
    myFixture.configureByText("a.java", "import foo.F<caret>oo;");
    performCopy();
    myFixture.configureByText("b.java", "class Goo { <caret> }");
    performPaste();
    myFixture.checkResult("""
                            import foo.Foo;

                            class Goo { Foo }""");
  }

  @Test
  public void testPasteCorrectSignatureToJavadoc() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class Foo {
        void foo(int a) {}
        void foo<caret>(byte a) {}
      }
      """);
    performCopy();
    myFixture.configureByText("b.java", "/** <caret> */");
    performPaste();
    myFixture.checkResult("/** Foo#foo(byte)<caret> */");
  }

  @Test
  public void testPasteCorrectGenericSignatureToJavadoc() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class Foo {
        void foo<caret>(java.util.List<String[]> a) {}
      }
      """);
    performCopy();
    myFixture.configureByText("b.java", "/** <caret> */");
    performPaste();
    myFixture.checkResult("/** Foo#foo(java.util.List)<caret> */");
  }

  @Test
  public void testPasteOverloadedSignatureToAComment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class Foo {
        void foo<caret>(int a) {} //
        void foo(int a, int b) {}
      }
      """);
    performCopy();
    EdtTestUtil.runInEdtAndWait(
      () -> myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("//") + 2));
    performPaste();
    myFixture.checkResult("""
                            class Foo {
                              void foo(int a) {} //Foo.foo(int)<caret>
                              void foo(int a, int b) {}
                            }
                            """);
  }

  @Test
  public void testPasteOverloadedSignatureToCode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class Foo {
        void foo<caret>(int a) {}
        void foo(int a, int b) {}
        {
          //paste before comment
        }
      }
      """);
    performCopy();
    EdtTestUtil.runInEdtAndWait(
      () -> myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("//") - 2));
    performPaste();
    myFixture.checkResult("""
                            class Foo {
                              void foo(int a) {}
                              void foo(int a, int b) {}
                              {
                                  Foo.foo()  //paste before comment
                              }
                            }
                            """);
  }

  @Test
  public void testFqnInImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public class Foo {}");
    myFixture.configureByText("a.java", "import foo.F<caret>oo;");
    performCopy();
    myFixture.configureByText("b.java", "import <caret>");
    performPaste();
    myFixture.checkResult("import foo.Foo<caret>");
  }

  @Test
  public void testCopyFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile psiFile = myFixture.addFileToProject("x/x.txt", "");
    assertTrue(CopyReferenceAction.doCopy(psiFile, myFixture.getProject()));

    String name = myFixture.getTestName(false);
    myFixture.configureByFile(name + "_dst.java");
    performPaste();
    myFixture.checkResultByFile(name + "_after.java");
  }

  @Test
  public void testCopyLineNumber() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """

      <caret>class Foo {
      }""");
    performCopy();
    myFixture.configureByText("a.txt", "");
    performPaste();
    myFixture.checkResult("a.java:2");
  }

  @Test
  public void testMethodOverloadCopy() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class Koo {
        public void foo(int a) { }
        public void foo(boolean a) { }
       \s
        {
          fo<caret>o(true);
        }
      }""");
    performCopy();
    myFixture.configureByText("b.java", """
      /**
       * <caret>
       */
      class Koo2 { }
      """);
    performPaste();
    myFixture.checkResult("""
                            /**
                             * Koo#foo(boolean)<caret>
                             */
                            class Koo2 { }
                            """);
  }

  @Test
  public void testModuleNameCopy() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("module-info.java", "module M16<caret> { }");
    performCopy();
    assertEquals("M16", CopyPasteManager.getInstance().getContents(CopyReferenceAction.ourFlavor));
  }

  @Test
  public void testModuleReferenceCopy() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("module-info.java", "module M16 { requires M16<caret>; }");
    performCopy();
    assertEquals("M16", CopyPasteManager.getInstance().getContents(CopyReferenceAction.ourFlavor));
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    myFixture.configureByFile(name + ".java");
    performCopy();
    myFixture.configureByFile(name + "_dst.java");
    performPaste();
    myFixture.checkResultByFile(name + "_after.java");
  }

  private void performCopy() {
    EdtTestUtil.runInEdtAndWait(() -> codeInsightFixture.get().testAction(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY_REFERENCE)));
  }

  private void performPaste() {
    codeInsightFixture.get().performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
  }
}