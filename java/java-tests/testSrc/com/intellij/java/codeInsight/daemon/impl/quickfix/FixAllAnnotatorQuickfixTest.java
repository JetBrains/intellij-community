// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.codeInsight.daemon.impl.HighlightVisitorBasedInspection;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixOptions;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class FixAllAnnotatorQuickfixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(
    projectFixture, List::of,
    LightDaemonAnalyzerOptions.DEFAULT.withBasePath("/java/java-tests/testData/codeInsight/daemonCodeAnalyzer/quickFix/fixAllAnnotator"));

  @Test
  public void testAnnotator() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    Annotator annotator = new MyAnnotator();
    Language javaLanguage = JavaFileType.INSTANCE.getLanguage();
    LanguageAnnotators.INSTANCE.addExplicitExtension(javaLanguage, annotator);
    daemon.getFixture().enableInspections(new HighlightVisitorBasedInspection().setRunAnnotators(true));
    try {
      //todo: use lightQuickFixFixture with a parameterized test to get separate tests for all data files in testData directory.
      LightQuickFixFixture quickFix = daemon.quickFixFixture(LightQuickFixOptions.DEFAULT.withActionAvailableAfterExecution());
      for (String fileName : lightQuickFixTestFiles(quickFix.getTestDataPath())) {
        quickFix.doTest(fileName);
      }
    }
    finally {
      LanguageAnnotators.INSTANCE.removeExplicitExtension(javaLanguage, annotator);
    }
  }

  public static class MyAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      if (element instanceof PsiMethod) {
        holder.newSilentAnnotation(HighlightSeverity.ERROR).range(((PsiMethod)element).getNameIdentifier())
        .withFix(new MyFix())
          .newFix(new MyFix()).batch().registerFix()
        .textAttributes(JavaHighlightingColors.DOC_COMMENT_TAG_VALUE).create();
      }
    }

    static class MyFix implements IntentionAction, LocalQuickFix {

      @NotNull
      @Override
      public String getText() {
        return getName();
      }

      @NotNull
      @Override
      public String getName() {
        return "MyFix";
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return getName();
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (element != null) {
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiMethod) {
            ((PsiMethod)parent).setName(((PsiMethod)parent).getName() + "F");
          }
        }
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
        return true;
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
        fail();
      }

      @Override
      public boolean startInWriteAction() {
        return true;
      }
    }
  }
}