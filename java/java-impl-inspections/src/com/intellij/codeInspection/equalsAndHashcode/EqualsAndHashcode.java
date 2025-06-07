// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

    @Override
    public @NotNull String getFamilyName() {
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

    @Override
    public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
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
