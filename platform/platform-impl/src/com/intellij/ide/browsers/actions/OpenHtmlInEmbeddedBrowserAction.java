// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.OpenInRightSplitAction;
import com.intellij.ide.browsers.WebBrowserXmlService;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.scale.ScaleContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
class OpenHtmlInEmbeddedBrowserAction extends DumbAwareAction {
  OpenHtmlInEmbeddedBrowserAction() {
    super(IdeBundle.message("action.open.web.preview.text"), null, AppUIUtil.loadSmallApplicationIcon(ScaleContext.create()));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    PsiFile psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE);
    //todo[dima.batrak] No way to protect of focus stealing. The focus will be in the newly opened file
    OpenInRightSplitAction.Companion.openInRightSplit(project, new WebPreviewVirtualFile(psiFile.getVirtualFile()), null, false);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    boolean enabled = project != null && psiFile != null && WebBrowserXmlService.getInstance().isHtmlFile(psiFile.getVirtualFile());
    e.getPresentation().setEnabledAndVisible(enabled);
  }
}
