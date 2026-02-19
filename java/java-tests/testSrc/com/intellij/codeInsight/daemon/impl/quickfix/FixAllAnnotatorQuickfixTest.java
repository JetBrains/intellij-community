// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightVisitorBasedInspection;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
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
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class FixAllAnnotatorQuickfixTest extends LightQuickFixTestCase {
  public void testAnnotator() {
    Annotator annotator = new MyAnnotator();
    Language javaLanguage = JavaFileType.INSTANCE.getLanguage();
    LanguageAnnotators.INSTANCE.addExplicitExtension(javaLanguage, annotator);
    enableInspectionTool(new HighlightVisitorBasedInspection().setRunAnnotators(true));
    try {
      //todo: use LightQuickFixParameterizedTestCase to get separate tests for all data files in testData directory.
      doAllTests(createWrapper());
    }
    finally {
      LanguageAnnotators.INSTANCE.removeExplicitExtension(javaLanguage, annotator);
    }
  }

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return true;
  }

  @Override
  @NonNls
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/fixAllAnnotator";
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