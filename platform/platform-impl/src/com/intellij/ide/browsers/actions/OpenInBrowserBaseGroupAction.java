// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.ide.browsers.WebBrowserXmlService;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.util.CachedValueImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class OpenInBrowserBaseGroupAction extends ActionGroup implements DumbAware {
  private CachedValue<AnAction[]> myChildren;

  protected OpenInBrowserBaseGroupAction(boolean popup) {
    Presentation p = getTemplatePresentation();
    p.setPopupGroup(popup);
    p.setHideGroupIfEmpty(true);
    p.setText(IdeBundle.messagePointer("open.in.browser"));
    p.setDescription(IdeBundle.messagePointer("open.selected.file.in.browser"));
    p.setIconSupplier(() -> AllIcons.Nodes.PpWeb);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(project != null && TrustedProjects.isTrusted(project));
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    CachedValue<AnAction[]> children = myChildren;
    if (children == null) {
      children = new CachedValueImpl<>(() -> {
        AnAction[] actions = computeChildren();
        return CachedValueProvider.Result.create(actions, WebBrowserManager.getInstance());
      });
      myChildren = children;
    }
    return children.getValue();
  }

  private AnAction @NotNull [] computeChildren() {
    List<WebBrowser> browsers = WebBrowserManager.getInstance().getBrowsers();
    boolean addDefaultBrowser = isPopup();
    boolean hasLocalBrowser = hasLocalBrowser();
    int offset = 0;
    if (addDefaultBrowser) offset++;
    if (hasLocalBrowser) offset++;
    AnAction[] actions = new AnAction[browsers.size() + offset];

    if (hasLocalBrowser) {
      actions[0] = new OpenHtmlInEmbeddedBrowserAction();
    }

    if (addDefaultBrowser) {
      OpenFileInDefaultBrowserAction defaultBrowserAction = new OpenFileInDefaultBrowserAction();
      defaultBrowserAction.getTemplatePresentation().setText(IdeBundle.messagePointer("default"));
      defaultBrowserAction.getTemplatePresentation().setIconSupplier(() -> AllIcons.Nodes.PpWeb);
      actions[hasLocalBrowser ? 1 : 0] = defaultBrowserAction;
    }

    for (int i = 0, size = browsers.size(); i < size; i++) {
      actions[i + offset] = new BaseOpenInBrowserAction(browsers.get(i));
    }
    return actions;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public static boolean hasLocalBrowser() {
    return JBCefApp.isSupported() && Registry.is("ide.web.preview.enabled", true);
  }

  public static final class OpenInBrowserGroupAction extends OpenInBrowserBaseGroupAction {
    public OpenInBrowserGroupAction() {
      super(true);
    }
  }

  public static class OpenInBrowserEditorContextBarGroupAction extends OpenInBrowserBaseGroupAction {
    public OpenInBrowserEditorContextBarGroupAction() {
      super(false);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      final WebBrowserManager browserManager = WebBrowserManager.getInstance();
      PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
      boolean needShowOnHover = psiFile != null && WebBrowserXmlService.getInstance().isXmlLanguage(psiFile.getViewProvider().getBaseLanguage())
              ? browserManager.isShowBrowserHoverXml()
              : browserManager.isShowBrowserHover();
      boolean enabled = needShowOnHover &&
                        (!browserManager.getActiveBrowsers().isEmpty() || OpenInBrowserBaseGroupAction.hasLocalBrowser())
                        && editor != null && !DiffUtil.isDiffEditor(editor);
      e.getPresentation().setEnabledAndVisible(enabled);
    }
  }
}