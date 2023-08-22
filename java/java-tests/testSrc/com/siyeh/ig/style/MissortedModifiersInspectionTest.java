// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class MissortedModifiersInspectionTest extends LightJavaInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void testMissortedModifiers() {
    doTest();
  }

  public void testIgnoreAnnotations() {
    doTest();
  }

  public void testTypeUseWithType() {
    doTestQuickFix();
  }

  public void testSimpleComment() {
    doTestQuickFix();
  }

  public void testAnotherComment() {
    doTestQuickFix();
  }

  public void testKeepAnnotationOrder() {
    doTestQuickFix();
  }

  public void testMissortedInModuleRequires(){
    doTestQuickFix();
  }

  public void testSealedClass() {
    doTestQuickFix();
  }

  public void doTestQuickFix() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("missorted.modifiers.sort.quickfix"));
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    MissortedModifiersInspection inspection = new MissortedModifiersInspection();
    if (getTestName(false).contains("TypeUseWithType")) {
      inspection.typeUseWithType = true;
    }
    else if (getTestName(false).contains("IgnoreAnnotations")) {
      inspection.m_requireAnnotationsFirst = false;
    }
    return inspection;
  }
}
