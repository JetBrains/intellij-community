// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class MismatchedCollectionQueryUpdateInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9_ANNOTATED);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

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

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ImplicitUsageProvider.EP_NAME, TEST_PROVIDER, myFixture.getTestRootDisposable());
    EntryPointsManagerBase.DEAD_CODE_EP_NAME.getExtensionList();
  }

  @Test
  public void testMismatchedCollectionQueryUpdate() {
    inspectionFixture.get().doTest();
  }
  
  @Test
  public void testUsedAssignmentResult() {
    inspectionFixture.get().doTest();
  }

  private static InspectionProfileEntry getInspection() {
    MismatchedCollectionQueryUpdateInspection inspection = new MismatchedCollectionQueryUpdateInspection();
    inspection.ignoredClasses.add("com.siyeh.igtest.bugs.mismatched_collection_query_update.ConstList");
    return inspection;
  }
}