// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection.ConversionStrategy;
import com.intellij.refactoring.BaseRefactoringProcessor;
import org.jetbrains.annotations.NotNull;

public class ClassCanBeRecordInspectionNoAccessorsRenamingTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new ClassCanBeRecordInspection(ConversionStrategy.DO_NOT_SUGGEST, false)};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/classCanBeRecord/noAccessorsRenaming";
  }

  @Override
  public void runSingle() throws Throwable {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(super::runSingle);
  }
}
