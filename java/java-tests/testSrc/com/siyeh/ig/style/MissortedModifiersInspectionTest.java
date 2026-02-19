// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
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

  public void testTypeUseWithTypeUseGenerated() {
    doTestQuickFix();
  }
  public void testTypeUseWithTypeUseGeneratedInline() {
    doTestQuickFix();
  }

  public void testTypeUseWithTypeUseNotGenerated() {
    doTest();
  }

  public void testTypeUseWithTypeHighlightingUseNotGenerated() {
    doTest();
  }

  public void testTypeUseWithTypeHighlightingUseGenerated() {
    doTest();
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

  @Override
  public void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings instance = JavaCodeStyleSettings.getInstance(getProject());
    instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = false;
    if (getTestName(false).contains("TypeUseWithType")) {
      instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = true;
    }
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    MissortedModifiersInspection inspection = new MissortedModifiersInspection();
    if (getTestName(false).contains("IgnoreAnnotations")) {
      inspection.m_requireAnnotationsFirst = false;
    }
    if (getTestName(false).contains("UseGenerated")) {
      inspection.typeUseWithType = true;
    }
    return inspection;
  }
}
