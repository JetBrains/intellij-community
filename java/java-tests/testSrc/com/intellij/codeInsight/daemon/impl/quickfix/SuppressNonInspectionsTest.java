// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclarationLocalInspection;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;


public class SuppressNonInspectionsTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_3;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    DeprecationInspection deprecationInspection = new DeprecationInspection();
    deprecationInspection.IGNORE_IN_SAME_OUTERMOST_CLASS = false;

    return new LocalInspectionTool[]{
      new RedundantThrowsDeclarationLocalInspection(),
      new SillyAssignmentInspection(),
      new AccessStaticViaInstance(),
      deprecationInspection,
      new JavaDocReferenceInspection(),
      new UncheckedWarningLocalInspection()
    };
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/suppressNonInspections";
  }

}

