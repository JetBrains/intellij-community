// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.templateLanguages;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.ErrorQuickFixProvider;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class TemplateLanguageErrorQuickFixProvider implements ErrorQuickFixProvider{

  @Override
  public void registerErrorQuickFix(final @NotNull PsiErrorElement errorElement, final @NotNull HighlightInfo.Builder highlightInfo) {
    final PsiFile psiFile = errorElement.getContainingFile();
    final FileViewProvider provider = psiFile.getViewProvider();
    if (!(provider instanceof TemplateLanguageFileViewProvider)) return;
    if (psiFile.getLanguage() != ((TemplateLanguageFileViewProvider)provider).getTemplateDataLanguage()) return;

    IntentionAction action = createChangeTemplateDataLanguageFix(errorElement);
    highlightInfo.registerFix(action, null, null, null, null);
  }

  public static IntentionAction createChangeTemplateDataLanguageFix(final PsiElement errorElement) {
    final PsiFile containingFile = errorElement.getContainingFile();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    final Language language = ((TemplateLanguageFileViewProvider) containingFile.getViewProvider()).getTemplateDataLanguage();
    return new IntentionAction() {

      @Override
      public @NotNull @Nls String getText() {
        return LangBundle.message("quickfix.change.template.data.language.text", language.getDisplayName());
      }

      @Override
      public @NotNull String getFamilyName() {
        return getText();
      }

      @Override
      public boolean isAvailable(final @NotNull Project project, final Editor editor, final PsiFile file) {
        return true;
      }

      @Override
      public void invoke(final @NotNull Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
        final TemplateDataLanguageConfigurable configurable = new TemplateDataLanguageConfigurable(project);
        ShowSettingsUtil.getInstance().editConfigurable(project, configurable, () -> {
          if (virtualFile != null) {
            configurable.selectFile(virtualFile);
          }
        });
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
  }

}
