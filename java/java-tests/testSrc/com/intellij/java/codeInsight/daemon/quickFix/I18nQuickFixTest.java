// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

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
    return new LocalInspectionTool[]{new I18nInspection()};
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
    // avoid "memory/disk conflict" when the document for changed annotation.xml stays in memory
    ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(getProject())).clearUncommittedDocuments();
    super.tearDown();
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
