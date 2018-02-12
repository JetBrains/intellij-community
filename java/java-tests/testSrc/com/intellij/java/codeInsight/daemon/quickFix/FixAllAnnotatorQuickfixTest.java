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

package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.impl.DefaultHighlightVisitorBasedInspection;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
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
    Language javaLanguage = StdFileTypes.JAVA.getLanguage();
    LanguageAnnotators.INSTANCE.addExplicitExtension(javaLanguage, annotator);
    enableInspectionTool(new DefaultHighlightVisitorBasedInspection.AnnotatorBasedInspection());
    try {
      doAllTests();
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
        Annotation annotation = holder.createErrorAnnotation(((PsiMethod)element).getNameIdentifier(), null);
        annotation.registerUniversalFix(new MyFix(), null, null);
        annotation.setTextAttributes(JavaHighlightingColors.DOC_COMMENT_TAG_VALUE);
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
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return true;
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        fail();
      }

      @Override
      public boolean startInWriteAction() {
        return true;
      }
    }
  }
}