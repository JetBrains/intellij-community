// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.TodoCommentInspection;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class TodoCommentInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @Test
  public void testNoDuplicates() {
    inspectionFixture.get().doTest("""
             class A {
             // /*TODO comment 'todo fixme'*/todo fixme/**/
             }""");
  }

  @Test
  public void testOnlyHighlightLineWithTodo() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.configureByText("X.java", """
                                /**
                                 * Very useful class
                                 * <warning descr="TODO comment 'TODO: to be or not to be'">TODO: to be or not to be</warning>
                                 *
                                 * @author turbanov
                                 */
                                class WithTodo {}""");
    myFixture.testHighlighting(true, false, false);
  }

  @Test
  public void testOnlyWarnOnEmpty() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.enableInspections(new TodoCommentInspection());
    myFixture.configureByText("X.java", """
      class X {
      //<warning descr="TODO comment without description">TODO</warning>
      //   <warning descr="TODO comment without description">TODO</warning>
      //<warning descr="TODO comment without description">TODO:</warning>
      //<warning descr="TODO comment without description">TODO :</warning>
      //<warning descr="TODO comment without description">TODO-</warning>
      //<warning descr="TODO comment without description">TODO - todo todo</warning>
      //TODO : OK, some description
      }""");
    myFixture.testHighlighting(true, false, false);
  }

  @Nullable
  private static InspectionProfileEntry getInspection() {
    final TodoCommentInspection inspection = new TodoCommentInspection();
    inspection.onlyWarnOnEmpty = false;
    return inspection;
  }
}