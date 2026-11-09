// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixture.INSPECTION_GADGETS_TEST_DATA_PATH;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class ForwardCompatibilityInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ForwardCompatibilityInspection(), ArrayUtilRt.EMPTY_STRING_ARRAY,
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/jdk/forward_compatibility");

  @Test
  public void testAssert() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_3, inspectionFixture.get()::doTest);
  }

  @Test
  public void testEnum() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_3, inspectionFixture.get()::doTest);
  }

  @Test
  public void testUnqualifiedYield() { inspectionFixture.get().doTest(); }

  @Test
  public void testQualifiedYieldWithUnexpectedToken() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> {
      myFixture.configureByFile(inspectionFixture.get().getTestName(false) + ".java");
      List<HighlightInfo> list = ContainerUtil.filter(myFixture.doHighlighting(HighlightSeverity.WARNING), it -> it.getSeverity().equals(HighlightSeverity.WARNING));
      assertEquals(1, list.size());
      HighlightInfo info = ContainerUtil.getOnlyItem(list);
      assertEquals("yield", info.getText());
      assertEquals("Unqualified call to 'yield' method is not supported in releases since Java 14", info.getDescription());
      assertEquals(60, info.getStartOffset());
      assertEquals(65, info.getEndOffset());
      assertFalse((boolean)info.findRegisteredQuickFix((descriptor, textRange) -> {
        return descriptor.getAction().getFamilyName().equals(InspectionGadgetsBundle.message("qualify.call.fix.family.name"));
      }));
    });
  }

  @Test
  public void testUnderscore() { inspectionFixture.get().doTest(); }

  @Test
  public void testRestrictedKeywordWarning() { inspectionFixture.get().doTest(); }

  @Test
  public void testLoneSemicolon() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture(); IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_20, inspectionFixture.get()::doTest); }

  @Test
  public void testModuleInfoWarning() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_9, () -> {
      myFixture.configureByFile("module-info.java");
      myFixture.testHighlighting(true, false, false);
    });
  }
}
