// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.refactoring.suggested.LightJavaCodeInsightFixtureTestCaseWithUtils;

public class JavaInheritDocNavigationTest extends LightJavaCodeInsightFixtureTestCaseWithUtils {
  public void test_inherit_doc() {
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

  public void test_inherit_doc_2() {
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

  public void test_inherit_doc_interface() {
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

  public void test_inherit_doc_explicit_super() {
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

  public void test_inherit_doc_return() {
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

  public void test_inherit_doc_stack_overflow() {
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

  public void test_inherit_doc_skip() {
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

    final var selectionModel = myFixture.getEditor().getSelectionModel();
    final var offset = selectionModel.getSelectionStart();
    navigateAndCheckLine("* @param b A::foo-b");
    final var caretModel = myFixture.getEditor().getCaretModel();
    caretModel.moveToOffset(offset);
    caretModel.moveCaretRelatively(0, -1, false, false, false);
    navigateAndCheckLine("* @param a B::foo-a");
  }

  public void test_inherit_doc_in_throws() {
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

  public void test_inherit_doc_in_method_type_parameter() {
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

  public void test_recursive_inherit_doc() {
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
    myFixture.performEditorAction("GotoDeclaration");
    final var selectionModel = myFixture.getEditor().getSelectionModel();
    selectionModel.selectLineAtCaret();
    assertEquals(expectedLine, selectionModel.getSelectedText().trim());
  }
}
