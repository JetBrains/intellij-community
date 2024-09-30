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

    navigateAndCheckLine(1, "* A doc");
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

    navigateAndCheckLine(1, "* B doc");
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

    navigateAndCheckLine(1, "* A doc");
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

    navigateAndCheckLine(0, "* @return return doc");
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

    navigateAndCheckLine(0, "* {@inheritDoc}");
  }

  private void navigateAndCheckLine(int lineOffset, String expectedLine) {
    myFixture.performEditorAction("GotoDeclaration");
    final var selectionModel = myFixture.getEditor().getSelectionModel();
    myFixture.getEditor().getCaretModel().moveCaretRelatively(0, lineOffset, false, false, false);
    selectionModel.selectLineAtCaret();
    assertEquals(expectedLine, selectionModel.getSelectedText().trim());
  }
}
