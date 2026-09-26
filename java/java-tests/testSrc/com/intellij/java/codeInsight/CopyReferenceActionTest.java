// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public class CopyReferenceActionTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/copyReference";
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES;
      super.runTestRunnable(testRunnable);
      return null;
    });
  }

  public void testConstructor() { doTest(); }
  public void testDefaultConstructor() { doTest(); }
  public void testIdentifierSeparator() { doTest(); }
  public void testMethodFromAnonymousClass() { doTest(); }

  public void testSameClassNames() {
    myFixture.addClass("package p; public class Foo {}");
    myFixture.configureByText("Foo.java", "package p1; public class Fo<caret>o {}");
    performCopy();
    myFixture.configureByText("a.java", "import p.Foo; class Bar extends Foo { <caret>}");
    performPaste();
    myFixture.checkResult("import p.Foo; class Bar extends Foo { p1.Foo}");
  }

  public void testAddImport() {
    myFixture.addClass("package foo; public class Foo {}");
    myFixture.configureByText("a.java", "import foo.F<caret>oo;");
    performCopy();
    myFixture.configureByText("b.java", "class Goo { <caret> }");
    performPaste();
    myFixture.checkResult("""
                            import foo.Foo;

                            class Goo { Foo }""");
  }

  public void testPasteCorrectSignatureToJavadoc() {
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

  public void testPasteCorrectGenericSignatureToJavadoc() {
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

  public void testPasteOverloadedSignatureToAComment() {
    myFixture.configureByText("a.java", """
      class Foo {
        void foo<caret>(int a) {} //
        void foo(int a, int b) {}
      }
      """);
    performCopy();
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("//") + 2);
    performPaste();
    myFixture.checkResult("""
                            class Foo {
                              void foo(int a) {} //Foo.foo(int)<caret>
                              void foo(int a, int b) {}
                            }
                            """);
  }

  public void testPasteOverloadedSignatureToCode() {
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
    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("//") - 2);
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

  public void testFqnInImport() {
    myFixture.addClass("package foo; public class Foo {}");
    myFixture.configureByText("a.java", "import foo.F<caret>oo;");
    performCopy();
    myFixture.configureByText("b.java", "import <caret>");
    performPaste();
    myFixture.checkResult("import foo.Foo<caret>");
  }

  public void testCopyFile() {
    PsiFile psiFile = myFixture.addFileToProject("x/x.txt", "");
    assertTrue(CopyReferenceAction.doCopy(psiFile, getProject()));

    String name = getTestName(false);
    myFixture.configureByFile(name + "_dst.java");
    performPaste();
    myFixture.checkResultByFile(name + "_after.java");
  }

  public void testCopyLineNumber() {
    myFixture.configureByText("a.java", """

      <caret>class Foo {
      }""");
    performCopy();
    myFixture.configureByText("a.txt", "");
    performPaste();
    myFixture.checkResult("a.java:2");
  }

  public void testMethodOverloadCopy() {
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

  public void testModuleNameCopy() {
    myFixture.configureByText("module-info.java", "module M16<caret> { }");
    performCopy();
    assertEquals("M16", CopyPasteManager.getInstance().getContents(CopyReferenceAction.ourFlavor));
  }

  public void testModuleReferenceCopy() {
    myFixture.configureByText("module-info.java", "module M16 { requires M16<caret>; }");
    performCopy();
    assertEquals("M16", CopyPasteManager.getInstance().getContents(CopyReferenceAction.ourFlavor));
  }

  private void doTest() {
    String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    performCopy();
    myFixture.configureByFile(name + "_dst.java");
    performPaste();
    myFixture.checkResultByFile(name + "_after.java");
  }

  private void performCopy() {
    myFixture.testAction(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY_REFERENCE));
  }

  private void performPaste() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
  }
}