/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.equalsAndHashcode;

import com.intellij.codeInsight.generation.GenerateEqualsHandler;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class EqualsAndHashcode extends EqualsAndHashcodeBase {

  @Override
  protected LocalQuickFix[] buildFixes(boolean isOnTheFly, boolean hasEquals) {
    if (!isOnTheFly) {
      return LocalQuickFix.EMPTY_ARRAY;
    }
    return new LocalQuickFix[]{new GenerateEqualsHashcodeFix(hasEquals)};
  }

  private static class GenerateEqualsHashcodeFix implements LocalQuickFix {

    private final boolean myHasEquals;

    GenerateEqualsHashcodeFix(boolean hasEquals) {
      myHasEquals = hasEquals;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return myHasEquals
             ? JavaBundle.message("inspection.equals.hashcode.generate.hashcode.quickfix")
             : JavaBundle.message("inspection.equals.hashcode.generate.equals.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (editor == null) {
        return;
      }
      final PsiElement element = descriptor.getPsiElement();
      new GenerateEqualsHandler().invoke(project, editor, element.getContainingFile());
    }

    @Nullable
    @Override
    public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
      return currentFile;
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      final @Nls String preview = myHasEquals
             ? JavaBundle.message("inspection.equals.hashcode.generate.hashcode.quickfix.preview")
             : JavaBundle.message("inspection.equals.hashcode.generate.equals.quickfix.preview");
      return new IntentionPreviewInfo.Html(preview);
    }
  }
}
