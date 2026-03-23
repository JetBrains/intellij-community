// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.openapi.editor.ex.EditorSettingsRefactoringOptionsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractInlineLocalDialog extends InlineOptionsDialog {
  public AbstractInlineLocalDialog(Project project, PsiElement variable, final PsiReference ref, int occurrencesCount) {
    super(project, true, variable);
    if (ref == null || occurrencesCount == 1) {
      setDoNotAskOption(new com.intellij.openapi.ui.DoNotAskOption() {
        @Override
        public boolean isToBeShown() {
          return EditorSettingsRefactoringOptionsProvider.getInstance().isShowInlineDialog();
        }

        @Override
        public void setToBeShown(boolean value, int exitCode) {
          EditorSettingsRefactoringOptionsProvider.getInstance().setShowInlineDialog(value);
        }

        @Override
        public boolean canBeHidden() {
          return true;
        }

        @Override
        public boolean shouldSaveOptionsOnCancel() {
          return false;
        }

        @Override
        public @NotNull String getDoNotShowMessage() {
          return UIBundle.message("dialog.options.do.not.show");
        }
      });
    }
  }
}
