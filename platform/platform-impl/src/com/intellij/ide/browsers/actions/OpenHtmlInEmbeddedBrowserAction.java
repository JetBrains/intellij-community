// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.OpenInRightSplitAction;
import com.intellij.ide.browsers.*;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.AppUIUtilKt;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.BitUtil;
import com.intellij.util.Url;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.util.Collection;

import static com.intellij.ide.browsers.OpenInBrowserRequestKt.createOpenInBrowserRequest;

/**
 * @author Konstantin Bulenkov
 */
final class OpenHtmlInEmbeddedBrowserAction extends DumbAwareAction {
  OpenHtmlInEmbeddedBrowserAction() {
    super(IdeBundle.messagePointer("action.open.web.preview.text"), null, new SynchronizedClearableLazy<>(() -> AppUIUtilKt.loadSmallApplicationIcon(ScaleContext.create(), 16, true)));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getRequiredData(CommonDataKeys.PROJECT);
    PsiFile psiFile = event.getRequiredData(CommonDataKeys.PSI_FILE);
    VirtualFile virtualFile = psiFile.getVirtualFile();
    boolean preferLocalFileUrl = BitUtil.isSet(event.getModifiers(), ActionEvent.SHIFT_MASK);

    try {
      OpenInBrowserRequest browserRequest = createOpenInBrowserRequest(psiFile, false);
      if (browserRequest == null) return;
      browserRequest.setReloadMode(WebBrowserManager.getInstance().getWebPreviewReloadMode());
      Collection<Url> urls = WebBrowserService.getInstance().getUrlsToOpen(browserRequest, preferLocalFileUrl);
      if (!urls.isEmpty()) {
        BaseOpenInBrowserActionKt.chooseUrl(urls).onSuccess((url) -> {
          WebPreviewVirtualFile file = new WebPreviewVirtualFile(virtualFile, url);
          if (!FileEditorManager.getInstance(project).isFileOpen(file)) {
            OpenInRightSplitAction.Companion.openInRightSplit(
              project,
              file,
              null,
              false
            );
          }
          else {
            FileEditorManagerEx.getInstanceEx(project).openFile(file, null, new FileEditorOpenOptions().withReuseOpen());
          }
        });
      }
    }
    catch (WebBrowserUrlProvider.BrowserException e) {
      Messages.showErrorDialog(e.getMessage(), IdeBundle.message("browser.error"));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    OpenInBrowserRequest request = BaseOpenInBrowserAction.Handler.doUpdate(e);
    Project project = e.getProject();
    PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    boolean enabled = project != null && psiFile != null && request != null && psiFile.getVirtualFile() != null;
    e.getPresentation().setEnabledAndVisible(enabled);
    if (!enabled) return;

    if (WebBrowserXmlService.getInstance().isHtmlFile(request.getFile())
        && ActionPlaces.CONTEXT_TOOLBAR.equals(e.getPlace())) {
      String text = getTemplateText();
      text += " (" + IdeBundle.message("browser.shortcut") + ")";
      e.getPresentation().setText(text);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
