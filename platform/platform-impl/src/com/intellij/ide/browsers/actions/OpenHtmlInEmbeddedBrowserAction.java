// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.OpenInRightSplitAction;
import com.intellij.ide.browsers.OpenInBrowserRequest;
import com.intellij.ide.browsers.WebBrowserService;
import com.intellij.ide.browsers.WebBrowserUrlProvider;
import com.intellij.ide.browsers.WebBrowserXmlService;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.BitUtil;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;
import java.util.Collection;

import static com.intellij.ide.browsers.OpenInBrowserRequestKt.createOpenInBrowserRequest;

/**
 * @author Konstantin Bulenkov
 */
class OpenHtmlInEmbeddedBrowserAction extends DumbAwareAction {
  OpenHtmlInEmbeddedBrowserAction() {
    super(IdeBundle.message("action.open.web.preview.text"), null, AppUIUtil.loadSmallApplicationIconForRelease(ScaleContext.create(), 16));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getRequiredData(CommonDataKeys.PROJECT);
    PsiFile psiFile = event.getRequiredData(CommonDataKeys.PSI_FILE);
    boolean preferLocalFileUrl = BitUtil.isSet(event.getModifiers(), InputEvent.SHIFT_MASK);

    try {
      OpenInBrowserRequest browserRequest = createOpenInBrowserRequest(psiFile, false);
      if (browserRequest == null) return;
      Collection<Url> urls = WebBrowserService.getInstance().getUrlsToOpen(browserRequest, preferLocalFileUrl);
      if (!urls.isEmpty()) {
        BaseOpenInBrowserActionKt.chooseUrl(urls).onSuccess((url) -> {
          OpenInRightSplitAction.Companion.openInRightSplit(
            project,
            new WebPreviewVirtualFile(psiFile.getVirtualFile(), url),
            null,
            false
          );
        });
      }
    }
    catch (WebBrowserUrlProvider.BrowserException e) {
      Messages.showErrorDialog(e.getMessage(), IdeBundle.message("browser.error"));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    OpenInBrowserRequest request = BaseOpenInBrowserAction.doUpdate(e);
    Project project = e.getProject();
    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    boolean enabled = project != null && psiFile != null && request != null;
    e.getPresentation().setEnabledAndVisible(enabled);
    if (!enabled) return;

    if (WebBrowserXmlService.getInstance().isHtmlFile(request.getFile())
        && ActionPlaces.CONTEXT_TOOLBAR == e.getPlace()) {
      String text = getTemplateText();
      text += " (" + IdeBundle.message("browser.shortcut") + ")";
      e.getPresentation().setText(text);
    }
  }
}
