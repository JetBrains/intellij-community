// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection.ConversionStrategy;

public class ClassCanBeRecordInspectionFlexibleConstructorBodiesTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    ClassCanBeRecordInspection inspection = new ClassCanBeRecordInspection(ConversionStrategy.DO_NOT_SUGGEST, true);
    return new LocalInspectionTool[]{inspection};
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_25;
  }

  @Override
  protected String getBasePath() {
    return "/inspection/classCanBeRecord/flexibleConstructorBodies";
  }

  @Override
  public void runSingle() throws Throwable {
    try {
      super.runSingle();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      // Verify that no content was changed. See IDEA-371645.
      checkResultByFile(getTestName(false) + ".java", getBasePath() + "/before" + getTestName(false), false);
    }


    super.runSingle();
  }
}
