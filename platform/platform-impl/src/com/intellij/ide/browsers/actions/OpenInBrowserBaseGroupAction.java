// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.ide.browsers.WebBrowserXmlService;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.ui.jcef.JBCefApp;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class OpenInBrowserBaseGroupAction extends ComputableActionGroup {
  private OpenFileInDefaultBrowserAction myDefaultBrowserAction;

  protected OpenInBrowserBaseGroupAction(boolean popup) {
    super(popup);

    Presentation p = getTemplatePresentation();
    p.setText(IdeBundle.message("open.in.browser"));
    p.setDescription(IdeBundle.message("open.selected.file.in.browser"));
    p.setIcon(AllIcons.Nodes.PpWeb);
  }

  @NotNull
  @Override
  protected final CachedValueProvider<AnAction[]> createChildrenProvider(@NotNull final ActionManager actionManager) {
    return () -> {
      List<WebBrowser> browsers = WebBrowserManager.getInstance().getBrowsers();
      boolean addDefaultBrowser = isPopup();
      boolean hasLocalBrowser = JBCefApp.isSupported() && Registry.is("ide.web.preview.enabled");
      int offset = 0;
      if (addDefaultBrowser) offset++;
      if (hasLocalBrowser) offset++;
      AnAction[] actions = new AnAction[browsers.size() + offset];

      if (hasLocalBrowser) {
        actions[0] = new OpenHtmlInEmbeddedBrowserAction();
      }

      if (addDefaultBrowser) {
        if (myDefaultBrowserAction == null) {
          myDefaultBrowserAction = new OpenFileInDefaultBrowserAction();
          myDefaultBrowserAction.getTemplatePresentation().setText(IdeBundle.message("default"));
          myDefaultBrowserAction.getTemplatePresentation().setIcon(AllIcons.Nodes.PpWeb);
        }
        actions[hasLocalBrowser ? 1 : 0] = myDefaultBrowserAction;
      }

      for (int i = 0, size = browsers.size(); i < size; i++) {
        actions[i + offset] = new BaseOpenInBrowserAction(browsers.get(i));
      }

      return CachedValueProvider.Result.create(actions, WebBrowserManager.getInstance());
    };
  }

  public static final class OpenInBrowserGroupAction extends OpenInBrowserBaseGroupAction {
    public OpenInBrowserGroupAction() {
      super(true);
    }
  }

  public static final class OpenInBrowserEditorContextBarGroupAction extends OpenInBrowserBaseGroupAction {
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
      e.getPresentation().setVisible(needShowOnHover && !browserManager.getActiveBrowsers().isEmpty() &&
                                     editor != null && !DiffUtil.isDiffEditor(editor));
    }
  }
}