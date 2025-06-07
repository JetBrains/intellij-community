// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class EqualsReplaceableByObjectsCallInspectionTest extends LightJavaInspectionTestCase {
  private EqualsReplaceableByObjectsCallInspection myInspection;

  @Override
  public void setUp() throws Exception {
    myInspection = new EqualsReplaceableByObjectsCallInspection();
    super.setUp();
    final InspectionProfileImpl profile = InspectionProfileManager.getInstance(getProject()).getCurrentProfile();
    profile.setErrorLevel(HighlightDisplayKey.find("EqualsReplaceableByObjectsCall"), HighlightDisplayLevel.WARNING, getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    myInspection = null;
    super.tearDown();
  }

  public void testEqualsReplaceableByObjectsCall() {
    testEqualsReplaceable(false);
  }

  public void testEqualsReplaceableByObjectsCallCheckNull() {
    testEqualsReplaceable(true);
  }

  protected void testEqualsReplaceable(boolean checkNotNull) {
    myInspection.checkNotNull = checkNotNull;
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.testHighlighting(true, true, false);
  }

  @Nullable
  @Override
  protected LocalInspectionTool getInspection() {
    return myInspection;
  }
}