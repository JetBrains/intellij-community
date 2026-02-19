// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.duplicateThrows.DuplicateThrowsInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclarationLocalInspection;
import org.jetbrains.annotations.NotNull;


public class Suppress15InspectionsTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTools(new UnusedDeclarationInspection());
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RedundantThrowsDeclarationLocalInspection(),
      new SillyAssignmentInspection(),
      new AccessStaticViaInstance(),
      new DeprecationInspection(),
      new JavaDocReferenceInspection(),
      new UncheckedWarningLocalInspection(),
      new DuplicateThrowsInspection()
    };
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/suppress15Inspections";
  }

}