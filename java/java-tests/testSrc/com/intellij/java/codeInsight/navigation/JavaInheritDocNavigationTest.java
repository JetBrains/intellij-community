// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class JavaInheritDocNavigationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void test_inherit_doc() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class A {
        /**
         * A doc
         */
        void foo() {}
      }
      
      class B extends A {
        /**
         * {@inherit<caret>Doc}
         */
        @Override
        void foo() {}
      }
      """);

    navigateAndCheckLine("* A doc");
  }

  @Test
  public void test_inherit_doc_2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class A {
        /**
         * A doc
         */
        void foo() {}
      }
      
      class B extends A {
        /**
         * B doc
         */
        @Override
        void foo() {}
      }

      class C extends B {
        /**
         * {@<caret>inheritDoc}
         */
        @Override
        void foo() {}
      }
      """);

    navigateAndCheckLine("* B doc");
  }

  @Test
  public void test_inherit_doc_interface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      interface A {
        /** A doc */
        void foo();
      }
      
      interface B extends A {
        /** B doc */
        @Override void foo();
      }
      
      interface C extends B {
        /** {@inherit<caret>Doc A} */
        @Override void foo();
      }
      """);

    navigateAndCheckLine("/** A doc */");
  }

  @Test
  public void test_inherit_doc_explicit_super() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class A {
        /**
         * A doc
         */
        void foo() {}
      }
      
      class B extends A {
        /**
         * B doc
         */
        @Override
        void foo() {}
      }

      class C extends B {
        /**
         * {@<caret>inheritDoc A}
         */
        @Override
        void foo() {}
      }
      """);

    navigateAndCheckLine("* A doc");
  }

  @Test
  public void test_inherit_doc_return() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class A {
        /**
         * A doc
         * @return return doc
         */
        int foo() { return 1; }
      }
      
      class B extends A {
        /**
         * B doc
         * @return {@<caret>inheritDoc}
         */
        @Override
        void foo() {}
      }
      """);

    navigateAndCheckLine("* @return return doc");
  }

  @Test
  public void test_inherit_doc_stack_overflow() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
    class Loop {
      static class A extends B {
        /**
        * {@<caret>inheritDoc}
        */
        void test() {}
      }
      static class B extends C {
        void test() {}
      }
    
      static class C extends B {
        void test() {}
      }
    }""");

    navigateAndCheckLine("* {@inheritDoc}");
  }

  @Test
  public void test_inherit_doc_skip() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      interface A {
        /**
         * @param a A::foo-a
         * @param b A::foo-b
         */
        void foo(int a, int b);
      }
      
      interface B extends A {
        /**
         * @param a B::foo-a
         */
        void foo(int a, int b);
      }
      
      interface C extends B {
        /**
         * @param a {@inheritDoc}
         * @param b {@inherit<caret>Doc}
         */
        void foo(int a, int b);
      }
      """);

    final int offset = EdtTestUtil.runInEdtAndGet(() -> {
      final var selectionModel = myFixture.getEditor().getSelectionModel();
      return selectionModel.getSelectionStart();
    });
    navigateAndCheckLine("* @param b A::foo-b");
    EdtTestUtil.runInEdtAndWait(() -> {
      final var caretModel = myFixture.getEditor().getCaretModel();
      caretModel.moveToOffset(offset);
      caretModel.moveCaretRelatively(0, -1, false, false, false);
    });
    navigateAndCheckLine("* @param a B::foo-a");
  }

  @Test
  public void test_inherit_doc_in_throws() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class A {
        /**
         * @throws E1 A::foo-E1
         */
        void foo() throws E1 {}
      }

      class B extends A {
        /**
         * @throws E1 {@inherit<caret>Doc}
         */
        @Override
        void foo() throws E1 {}
      }
    """);

    navigateAndCheckLine("* @throws E1 A::foo-E1");
  }

  @Test
  public void test_inherit_doc_in_method_type_parameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class A {
        /**
         * @param <T> A::foo-T
         */
        <T> void foo() {}
      }

      class B extends A {
        /**
         * @param <T> {@inherit<caret>Doc}
         */
        @Override
        <T> void foo() {}
      }
    """);

    navigateAndCheckLine("* @param <T> A::foo-T");
  }

  @Test
  public void test_recursive_inherit_doc() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      public interface A {
        /**
         * @param a A::m-param
         */
        void m(int a);
      }
      public interface B extends A {
        /**
         * @param a {@inheritDoc}
         */
        void m(int a);
      }
      public interface C extends B {
        /**
         * @param a {@inherit<caret>Doc B}
         */
        void m(int a);
      }
    """);

    navigateAndCheckLine("* @param a {@inheritDoc}");
  }

  private void navigateAndCheckLine(String expectedLine) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.performEditorAction("GotoDeclaration");
      final var selectionModel = myFixture.getEditor().getSelectionModel();
      selectionModel.selectLineAtCaret();
      assertEquals(expectedLine, selectionModel.getSelectedText().trim());
    });
  }
}
