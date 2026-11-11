// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class RedundantMethodOverrideInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  private final RedundantMethodOverrideInspection myInspection = new RedundantMethodOverrideInspection();

  private InspectionProfileEntry getInspection() {
    myInspection.checkLibraryMethods = true;
    myInspection.ignoreDelegates = false;
    return myInspection;
  }

  @Test
  public void testRedundantMethodOverride() { inspectionFixture.get().doTest(); }

  @Test
  public void testMutualRecursion() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    RecursionManager.disableMissedCacheAssertions(myFixture.getTestRootDisposable());
    inspectionFixture.get().doTest();
  }

  @Test
  public void testLibraryOverride() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.allowTreeAccessForAllFiles();
    inspectionFixture.get().doTest();
  }

  @Test
  public void testSwitch() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, inspectionFixture.get()::doTest);
  }

  @Test
  public void testInstanceOf() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, inspectionFixture.get()::doTest);
  }

  @Test
  public void testForEachPatterns() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_X, inspectionFixture.get()::doTest);
  }

  @Test
  public void testIgnoreDelegates(){
    boolean ignoreDelegatesOldValue = myInspection.ignoreDelegates;
    try {
      myInspection.ignoreDelegates = true;
      inspectionFixture.get().doTest();
    }
    finally {
      myInspection.ignoreDelegates = ignoreDelegatesOldValue;
    }
  }
}
