// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.templateLanguages;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.templateLanguages.TemplateDataLanguageMappings.getTemplateableLanguages;

public final class ChangeTemplateDataLanguageAction extends AnAction {
  @Override
  public void update(final @NotNull AnActionEvent e) {
    e.getPresentation().setVisible(false);

    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null && files.length > 1) {
      virtualFile = null;
    }
    if (virtualFile == null || virtualFile.isDirectory()) return;

    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    final FileViewProvider provider = PsiManager.getInstance(project).findViewProvider(virtualFile);
    if (provider instanceof ConfigurableTemplateLanguageFileViewProvider viewProvider) {
      e.getPresentation().setText(LangBundle.messagePointer("quickfix.change.template.data.language.text", viewProvider.getTemplateDataLanguage().getDisplayName()));
      e.getPresentation().setEnabledAndVisible(true);
    }

  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project == null || virtualFile == null) return;

    new ListPopupImpl(project, new TemplateDataLanguageChooserPopupStep(getTemplateableLanguages(), virtualFile, project))
      .showInBestPositionFor(e.getDataContext());
  }
}
