// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class DumbAwareAnnotatorInDumbModeTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testDumbAwareAnnotatorHighlightsCodeInDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("DumbAwareAnnotatorCheck.java", "class <error descr=\"null\">DumbAwareAnnotatorCheck</error> {}");
    Language javaLanguage = JavaFileType.INSTANCE.getLanguage();
    MyDumbAwareAnnotator dumbAwareAnnotator = new MyDumbAwareAnnotator();
    LanguageAnnotators.INSTANCE.addExplicitExtension(javaLanguage, dumbAwareAnnotator);
    CodeInsightTestFixtureImpl.mustWaitForSmartMode(false, myFixture.getTestRootDisposable());
    try {
      DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> {
        myFixture.testHighlighting();
      });
    }
    finally {
      LanguageAnnotators.INSTANCE.removeExplicitExtension(javaLanguage, dumbAwareAnnotator);
    }
  }

  private static class MyDumbAwareAnnotator implements Annotator, DumbAware {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      assertTrue(DumbService.getInstance(element.getProject()).isDumb());
      if (element instanceof PsiClass) {
        holder.newSilentAnnotation(HighlightSeverity.ERROR).range(((PsiClass)element).getNameIdentifier()).create();
      }
    }
  }
}
