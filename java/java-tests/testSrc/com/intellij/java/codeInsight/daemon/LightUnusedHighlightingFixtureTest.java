// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiNamedElement;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightUnusedHighlightingFixtureTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnusedDeclarationInspection(true));
    ImplicitUsageProvider.EP_NAME.getPoint().registerExtension(new ImplicitUsageProvider() {
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
        return element instanceof PsiField && (
          "implicitWritePublic".equals(((PsiNamedElement)element).getName()) ||
          "implicitWriteProtected".equals(((PsiNamedElement)element).getName()) ||
          "implicitWritePackagePrivate".equals(((PsiNamedElement)element).getName())
        );
      }
    }, getTestRootDisposable());
  }

  public void testMarkFieldsWhichAreExplicitlyWrittenAsUnused() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  public void testConflictingIgnoreParameters() {
    String testFileName = getTestName(false);
    myFixture.configureByFile(testFileName + ".java");
    IntentionAction action = myFixture.getAvailableIntention(
      CodeInsightBundle.message("rename.named.element.text", "foo", "ignored1"));
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResultByFile(testFileName + "_after.java", true);
  }

  public void testBrokenClassToImplicitClass() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_CLASSES.getMinimumLevel(), ()->{
      myFixture.configureByFile(getTestName(false) + ".java");
      myFixture.checkHighlighting();
    });
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advFixture";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
