// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class UnusedReturnValueQuickFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/unusedReturnValue/quickFix");
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ImplicitUsageProvider.EP_NAME, new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitRead(@NotNull PsiElement element) {
        return element instanceof PsiMethod && ((PsiMethod)element).getName().equals("implicitRead");
      }

      @Override
      public boolean isImplicitWrite(@NotNull PsiElement element) {
        return false;
      }
    }, myFixture.getTestRootDisposable());
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), EntryPointsManagerBase.DEAD_CODE_EP_NAME, new EntryPoint() {
      @Override
      public void readExternal(Element element) throws InvalidDataException { }

      @Override
      public void writeExternal(Element element) throws WriteExternalException { }

      @NotNull
      @Override
      public String getDisplayName() {
        return "return value used";
      }

      @Override
      public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
        return isEntryPoint(psiElement);
      }

      @Override
      public boolean isEntryPoint(@NotNull PsiElement psiElement) {
        return psiElement instanceof PsiMethod && "provider".equals(((PsiMethod)psiElement).getName());
      }

      @Override
      public boolean isSelected() {
        return true;
      }

      @Override
      public void setSelected(boolean selected) { }
    }, myFixture.getTestRootDisposable());

    myFixture.enableInspections(new UnusedReturnValue());
  }

  @Test
  public void testSideEffects() {
    doTest();
  }
  @Test
  public void testSideEffectsComplex() {
    doTest();
  }
  @Test
  public void testSideEffectsComplex2() {
    doTest();
  }
  @Test
  public void testRedundantReturn() {
    doTest();
  }
  @Test
  public void testNoChangeForImplicitRead() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = myFixture.getTestName(false);
    myFixture.configureByFile(name + ".java");
    assertThat(myFixture.filterAvailableIntentions(JavaBundle.message("inspection.unused.return.value.make.void.quickfix"))).isNullOrEmpty();
  }

  @Test
  public void testNoChangeForEntryPoint() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = myFixture.getTestName(false);
    myFixture.configureByFile(name + ".java");
    assertThat(myFixture.filterAvailableIntentions(JavaBundle.message("inspection.unused.return.value.make.void.quickfix"))).isNullOrEmpty();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = myFixture.getTestName(false);
    myFixture.configureByFile(name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(JavaBundle.message("inspection.unused.return.value.make.void.quickfix")));
      myFixture.checkResultByFile(name + ".after.java");
    });
  }
}
