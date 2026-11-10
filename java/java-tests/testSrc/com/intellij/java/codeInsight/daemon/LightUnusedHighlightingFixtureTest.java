// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiNamedElement;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class LightUnusedHighlightingFixtureTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advFixture");
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
    }, myFixture.getTestRootDisposable());
  }

  @Test
  public void testMarkFieldsWhichAreExplicitlyWrittenAsUnused() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  @Test
  public void testConflictingIgnoreParameters() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String testFileName = myFixture.getTestName(false);
    myFixture.configureByFile(testFileName + ".java");
    IntentionAction action = myFixture.getAvailableIntention(
      CodeInsightBundle.message("rename.named.element.text", "foo", "ignored1"));
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResultByFile(testFileName + "_after.java", true);
  }

  @Test
  public void testBrokenClassToImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_CLASSES.getStandardLevel(), ()->{
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.checkHighlighting();
    });
  }

  @Test
  public void testSeveralMainMethods() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_25, ()->{
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.checkHighlighting();
    });
  }

  @Test
  public void testMainMethodInInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_25, ()->{
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.checkHighlighting();
    });
  }
}
