// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.ServiceContainerUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class MismatchedCollectionQueryUpdateInspectionTest extends LightJavaInspectionTestCase {

  private static final ImplicitUsageProvider TEST_PROVIDER = new ImplicitUsageProvider() {
    @Override
    public boolean isImplicitUsage(@NotNull PsiElement element) {
      return false;
    }

    @Override
    public boolean isImplicitRead(@NotNull PsiElement element) {
      return false;
    }

    @Override
    public boolean isImplicitWrite(@NotNull PsiElement element) {
      return element instanceof PsiField && "injected".equals(((PsiField)element).getName());
    }
  };

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ImplicitUsageProvider.EP_NAME, TEST_PROVIDER, myFixture.getTestRootDisposable());
    EntryPointsManagerBase.DEAD_CODE_EP_NAME.getExtensionList();
  }

  public void testMismatchedCollectionQueryUpdate() {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9_ANNOTATED;
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    MismatchedCollectionQueryUpdateInspection inspection = new MismatchedCollectionQueryUpdateInspection();
    inspection.ignoredClasses.add("com.siyeh.igtest.bugs.mismatched_collection_query_update.ConstList");
    return inspection;
  }
}