// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
@TestDataPath("$CONTENT_ROOT/testData/ig/com/siyeh/igtest/bugs/comparable_implemented_but_equals_not_overridden")
public class ComparableImplementedButEqualsNotOverriddenInspectionTest extends LightJavaInspectionTestCase {

  public void testInterfaceImplementingComparable() { doTest(); }

  public void testSimple() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.fix.generate.equals.name"));
  }

  public void testSimpleMarkdown() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.fix.add.note.name"));
  }

  public void testNoteMarkdown() {
    doTest();
  }

  public void testAddNoteToExistingComments() {
    doTest();
    checkQuickFixAll();
  }

  public void testAbstractClass1() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_22, () -> {
      doTest();
      checkQuickFix(InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.fix.add.note.name"));
    });
  }

  public void testAbstractClass2() { doTest(); }
  public void testAbstractClass3() { doTest(); }
  public void testNote() { doTest(); }
  public void testNoWarningForRecord() { doTest(); }

  public void testNoFixForAnonymousClass() {
    doTest();
    assertNotNull(myFixture.findSingleIntention(
      InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.fix.generate.equals.name")));
    assertEmpty(myFixture.filterAvailableIntentions(
      InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.fix.add.note.name")));
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ComparableImplementedButEqualsNotOverriddenInspection();
  }
}