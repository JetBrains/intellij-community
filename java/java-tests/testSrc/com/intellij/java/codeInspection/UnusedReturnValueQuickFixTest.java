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
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class UnusedReturnValueQuickFixTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
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
    }, getTestRootDisposable());
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
    }, getTestRootDisposable());

    myFixture.enableInspections(new UnusedReturnValue());
  }

  public void testSideEffects() { doTest(); }
  public void testSideEffectsComplex() { doTest(); }
  public void testSideEffectsComplex2() { doTest(); }
  public void testRedundantReturn() { doTest(); }
  public void testNoChangeForImplicitRead() {
    final String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    assertEmpty(myFixture.filterAvailableIntentions(JavaBundle.message("inspection.unused.return.value.make.void.quickfix")));
  }

  public void testNoChangeForEntryPoint() {
    final String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    assertEmpty(myFixture.filterAvailableIntentions(JavaBundle.message("inspection.unused.return.value.make.void.quickfix")));
  }

  private void doTest() {
    final String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(JavaBundle.message("inspection.unused.return.value.make.void.quickfix")));
    myFixture.checkResultByFile(name + ".after.java");
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/unusedReturnValue/quickFix";
  }
}
