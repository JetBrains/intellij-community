// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class LimitedScopeInnerClassInspectionInfoTest extends LightJavaInspectionTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();
    HighlightDisplayKey displayKey = HighlightDisplayKey.find(getInspection().getShortName());
    InspectionProfileImpl currentProfile = ProjectInspectionProfileManager.getInstance(project).getCurrentProfile();
    currentProfile.setErrorLevel(displayKey, HighlightDisplayLevel.DO_NOT_SHOW, project);
  }

  public void testUncompletedNestedClass() {
    doTest("""
             class Example {
                 void test() {
                     class Loc<caret>al<EOLError descr="'{' expected"></EOLError>
                 }
             }""");
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new LimitedScopeInnerClassInspection();
  }
}