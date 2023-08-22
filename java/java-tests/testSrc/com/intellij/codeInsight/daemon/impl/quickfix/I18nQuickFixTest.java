// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;


public class I18nQuickFixTest extends LightQuickFixParameterizedTestCase {
  private boolean myMustBeAvailableAfterInvoke;

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    I18nInspection inspection = new I18nInspection();
    inspection.setReportUnannotatedReferences(true);
    return new LocalInspectionTool[]{inspection};
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/i18n";
  }

  @Override
  protected void beforeActionStarted(final String testName, final String contents) {
    myMustBeAvailableAfterInvoke = Comparing.strEqual(testName, "SystemCall.java");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      // avoid "memory/disk conflict" when the document for changed annotation.xml stays in memory
      ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(getProject())).clearUncommittedDocuments();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  public void runSingle() throws Throwable {
    VfsGuardian.guard(FileUtil.toSystemIndependentName(PlatformTestUtil.getCommunityPath()), getTestRootDisposable());
    super.runSingle();
  }

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return myMustBeAvailableAfterInvoke;
  }
}
