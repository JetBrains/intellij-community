// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ClassCanBeRecordInspectionUsageProviderTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    /*
      Take in account the cases when a class is, for example, a Spring bean as in com.intellij.spring.code.SpringImplicitUsageProvider
     */
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ImplicitUsageProvider.EP_NAME, new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(@NotNull PsiElement element) {
        return true;
      }

      @Override
      public boolean isImplicitRead(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitWrite(@NotNull PsiElement element) {
        return false;
      }
    }, getTestRootDisposable());
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new ClassCanBeRecordInspection(ClassCanBeRecordInspection.ConversionStrategy.SILENTLY, true)};
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_15;
  }

  @Override
  protected String getBasePath() {
    return "/inspection/classCanBeRecord/usageProvider";
  }
}
