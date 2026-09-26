// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class RenameLabelTest extends LightJavaCodeInsightFixtureTestCase {
  public void testBreakLabelRename() {
    myFixture.configureByText("a.java", """

      class a {
        void m() {
          <caret>a_label: while (true) break a_label;
        }
      }""");
    myFixture.renameElementAtCaret("the_label");
    myFixture.checkResult("""

                            class a {
                              void m() {
                                the_label: while (true) break the_label;
                              }
                            }""");
  }

  public void testContinueLabelRename() {
    myFixture.configureByText("a.java", """

      class a {
        void m() {
          a_label: while (true) continue <caret>a_label;
        }
      }""");
    myFixture.renameElementAtCaret("the_label");
    myFixture.checkResult("""

                            class a {
                              void m() {
                                the_label: while (true) continue the_label;
                              }
                            }""");
  }
}