// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class RenameLabelTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testBreakLabelRename() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """

      class a {
        void m() {
          <caret>a_label: while (true) break a_label;
        }
      }""");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.renameElementAtCaret("the_label"));
    myFixture.checkResult("""

                            class a {
                              void m() {
                                the_label: while (true) break the_label;
                              }
                            }""");
  }

  @Test
  public void testContinueLabelRename() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """

      class a {
        void m() {
          a_label: while (true) continue <caret>a_label;
        }
      }""");
    EdtTestUtil.runInEdtAndWait(() -> myFixture.renameElementAtCaret("the_label"));
    myFixture.checkResult("""

                            class a {
                              void m() {
                                the_label: while (true) continue the_label;
                              }
                            }""");
  }
}