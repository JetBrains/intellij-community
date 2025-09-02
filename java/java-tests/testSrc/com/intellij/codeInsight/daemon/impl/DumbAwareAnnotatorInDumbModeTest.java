// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class DumbAwareAnnotatorInDumbModeTest extends LightJavaCodeInsightFixtureTestCase {
  public void testDumbAwareAnnotatorHighlightsCodeInDumbMode() {
    myFixture.configureByText("DumbAwareAnnotatorCheck.java", "class <error descr=\"null\">DumbAwareAnnotatorCheck</error> {}");
    Language javaLanguage = JavaFileType.INSTANCE.getLanguage();
    MyDumbAwareAnnotator dumbAwareAnnotator = new MyDumbAwareAnnotator();
    LanguageAnnotators.INSTANCE.addExplicitExtension(javaLanguage, dumbAwareAnnotator);
    ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject())).mustWaitForSmartMode(false, getTestRootDisposable());
    try {
      DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
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
