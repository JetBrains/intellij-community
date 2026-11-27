// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.classlayout;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.classlayout.ClassMayBeInterfaceInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ClassMayBeInterfaceFixTest {
  private final TestFixture<IGQuickFixesFixture> ig = igQuickFixesFixture(() -> {
    ClassMayBeInterfaceInspection inspection = new ClassMayBeInterfaceInspection();
    inspection.reportClassesWithNonAbstractMethods = true;
    return List.of(inspection);
  }, "classlayout/class_may_be_interface", InspectionGadgetsBundle.message("class.may.be.interface.convert.quickfix"));

  @Test
  public void testConvertMe() { ig.get().doTest(); }
  @Test
  public void testObjectMethods() { ig.get().doTest(); }
  @Test
  public void testDefaultConstructor() { ig.get().doTest(); }
  @Test
  public void testLocalClass() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture(); 
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_14, ig.get()::assertQuickfixNotAvailable); 
  }
  @Test
  public void testLocalClassJava15() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture(); 
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.HIGHEST, ig.get()::doTest); 
  }
}