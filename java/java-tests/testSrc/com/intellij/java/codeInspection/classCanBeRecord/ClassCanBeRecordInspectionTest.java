// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection.ConversionStrategy;
import com.intellij.refactoring.BaseRefactoringProcessor;
import org.jetbrains.annotations.NotNull;

public class ClassCanBeRecordInspectionTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    ClassCanBeRecordInspection inspection = new ClassCanBeRecordInspection(ConversionStrategy.DO_NOT_SUGGEST, true);
    inspection.myIgnoredAnnotations.add("my.annotation1.MyAnn");
    inspection.myIgnoredAnnotations.add("my.annotation2.*");
    return new LocalInspectionTool[]{inspection};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/classCanBeRecord";
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


    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(super::runSingle);
  }
}
