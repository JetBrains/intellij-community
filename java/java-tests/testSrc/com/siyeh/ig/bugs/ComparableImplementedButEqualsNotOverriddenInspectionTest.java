// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
@TestDataPath("$CONTENT_ROOT/testData/ig/com/siyeh/igtest/bugs/comparable_implemented_but_equals_not_overridden")
public class ComparableImplementedButEqualsNotOverriddenInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ComparableImplementedButEqualsNotOverriddenInspection());

  @Test
  public void testInterfaceImplementingComparable() { inspectionFixture.get().doTest(); }

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.fix.generate.equals.name"));
  }

  @Test
  public void testSimpleMarkdown() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.fix.add.note.name"));
  }

  @Test
  public void testNoteMarkdown() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testAddNoteToExistingComments() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }

  @Test
  public void testAbstractClass1() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22, () -> {
      inspectionFixture.get().doTest();
      inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.fix.add.note.name"));
    });
  }

  @Test
  public void testAbstractClass2() { inspectionFixture.get().doTest(); }
  @Test
  public void testAbstractClass3() { inspectionFixture.get().doTest(); }
  @Test
  public void testNote() { inspectionFixture.get().doTest(); }
  @Test
  public void testNoWarningForRecord() { inspectionFixture.get().doTest(); }

  @Test
  public void testNoFixForAnonymousClass() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    inspectionFixture.get().doTest();
    assertNotNull(myFixture.findSingleIntention(
      InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.fix.generate.equals.name")));
    assertThat(myFixture.filterAvailableIntentions(
      InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.fix.add.note.name"))).isNullOrEmpty();
  }
}