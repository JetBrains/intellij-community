// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.util;

import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaFeatureTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testWithAssumedFeatureOverridesIsAvailable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("Main.java", "class Main {}");
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> {
      assertFalse(PsiUtil.isAvailable(JavaFeature.INSTANCE_MAIN_METHOD, file));
      boolean result = JavaFeature.assumeAvailable(
        JavaFeature.INSTANCE_MAIN_METHOD,
        () -> PsiUtil.isAvailable(JavaFeature.INSTANCE_MAIN_METHOD, file)
      );
      assertTrue(result);
    });
  }

  @Test
  public void testWithAssumedFeatureDoesNotAffectOtherFeatures() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("Main.java", "class Main {}");
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> {
      boolean result = JavaFeature.assumeAvailable(
        JavaFeature.INSTANCE_MAIN_METHOD,
        () -> PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, file)
      );
      assertFalse(result);
    });
  }

  @Test
  public void testWithAssumedFeatureRestoresStateAfterExecution() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("Main.java", "class Main {}");
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> {
      JavaFeature.assumeAvailable(
        JavaFeature.INSTANCE_MAIN_METHOD,
        () -> {
          assertTrue(PsiUtil.isAvailable(JavaFeature.INSTANCE_MAIN_METHOD, file));
          return null;
        }
      );
      assertFalse(JavaFeature.isAssumed(JavaFeature.INSTANCE_MAIN_METHOD));
      assertFalse(PsiUtil.isAvailable(JavaFeature.INSTANCE_MAIN_METHOD, file));
    });
  }

  @Test
  public void testNestedAssumeAvailableAccumulatesFeatures() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("Main.java", "class Main {}");
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> {
      JavaFeature.assumeAvailable(JavaFeature.INSTANCE_MAIN_METHOD, () -> {
        JavaFeature.assumeAvailable(JavaFeature.IMPLICIT_CLASSES, () -> {
          assertTrue(PsiUtil.isAvailable(JavaFeature.INSTANCE_MAIN_METHOD, file));
          assertTrue(PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, file));
          return null;
        });
        // inner feature restored, outer still active
        assertTrue(JavaFeature.isAssumed(JavaFeature.INSTANCE_MAIN_METHOD));
        assertFalse(JavaFeature.isAssumed(JavaFeature.IMPLICIT_CLASSES));
        return null;
      });
      assertFalse(JavaFeature.isAssumed(JavaFeature.INSTANCE_MAIN_METHOD));
    });
  }
}
