/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.ToolExtensionPoints;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class UnusedReturnValueQuickFixTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PlatformTestUtil.registerExtension(Extensions.getRootArea(), ImplicitUsageProvider.EP_NAME, new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitRead(PsiElement element) {
        return element instanceof PsiMethod && ((PsiMethod)element).getName().equals("implicitRead");
      }

      @Override
      public boolean isImplicitWrite(PsiElement element) {
        return false;
      }
    }, getTestRootDisposable());
    PlatformTestUtil.registerExtension(Extensions.getRootArea(), ExtensionPointName.create(ToolExtensionPoints.DEAD_CODE_TOOL), new EntryPoint() {
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
    assertEmpty(myFixture.filterAvailableIntentions(InspectionsBundle.message("inspection.unused.return.value.make.void.quickfix")));
  }

  public void testNoChangeForEntryPoint() {
    final String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    assertEmpty(myFixture.filterAvailableIntentions(InspectionsBundle.message("inspection.unused.return.value.make.void.quickfix")));
  }

  private void doTest() {
    final String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.launchAction(myFixture.findSingleIntention(InspectionsBundle.message("inspection.unused.return.value.make.void.quickfix")));
    myFixture.checkResultByFile(name + ".after.java");
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/unusedReturnValue/quickFix";
  }
}
