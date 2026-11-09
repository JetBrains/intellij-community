// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class LimitedScopeInnerClassInspectionInfoTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    Project project = myFixture.getProject();
    HighlightDisplayKey displayKey = HighlightDisplayKey.find(getInspection().getShortName());
    InspectionProfileImpl currentProfile = ProjectInspectionProfileManager.getInstance(project).getCurrentProfile();
    currentProfile.setErrorLevel(displayKey, HighlightDisplayLevel.DO_NOT_SHOW, project);
  }

  @Test
  public void testUncompletedNestedClass() {
    inspectionFixture.get().doTest("""
             class Example {
                 void test() {
                     class Loc<caret>al<EOLError descr="'{' expected"></EOLError>
                 }
             }""");
  }

  private static InspectionProfileEntry getInspection() {
    return new LimitedScopeInnerClassInspection();
  }
}