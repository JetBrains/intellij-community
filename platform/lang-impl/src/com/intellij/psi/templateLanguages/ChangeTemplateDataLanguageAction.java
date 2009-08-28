/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.templateLanguages;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.psi.PsiManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.lang.LangBundle;

/**
 * @author peter
 */
public class ChangeTemplateDataLanguageAction extends AnAction {
  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible(false);

    VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null && files.length > 1) {
      virtualFile = null;
    }
    if (virtualFile == null) return;

    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;

    final FileViewProvider provider = PsiManager.getInstance(project).findViewProvider(virtualFile);
    if (provider instanceof ConfigurableTemplateLanguageFileViewProvider) {
      final TemplateLanguageFileViewProvider viewProvider = (TemplateLanguageFileViewProvider)provider;

      e.getPresentation().setText(LangBundle.message("quickfix.change.template.data.language.text", viewProvider.getTemplateDataLanguage().getDisplayName()));
      e.getPresentation().setEnabled(true);
      e.getPresentation().setVisible(true);
    }

  }

  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    final TemplateDataLanguageConfigurable configurable = TemplateDataLanguageConfigurable.getInstance(project);
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable, new Runnable() {
      public void run() {
        if (virtualFile != null) {
          configurable.selectFile(virtualFile);
        }
      }
    });
  }


}
