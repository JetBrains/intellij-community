// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class RenameLabelTest extends LightJavaCodeInsightFixtureTestCase {
  void testBreakLabelRename() {
    myFixture.configureByText "a.java", """
        class a {
          void m() {
            <caret>a_label: while (true) break a_label;
          }
        }""".stripIndent()
    myFixture.renameElementAtCaret("the_label")
    myFixture.checkResult """
        class a {
          void m() {
            the_label: while (true) break the_label;
          }
        }""".stripIndent()
  }

  void testContinueLabelRename() {
    myFixture.configureByText "a.java", """
        class a {
          void m() {
            a_label: while (true) continue <caret>a_label;
          }
        }""".stripIndent()
    myFixture.renameElementAtCaret("the_label")
    myFixture.checkResult """
        class a {
          void m() {
            the_label: while (true) continue the_label;
          }
        }""".stripIndent()
  }
}