// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers;

import com.intellij.execution.filters.HyperlinkWithPopupMenuInfo;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;

public final class OpenUrlHyperlinkInfo implements HyperlinkWithPopupMenuInfo {
  private final String url;
  private final WebBrowser browser;
  private final Condition<? super WebBrowser> browserCondition;

  public OpenUrlHyperlinkInfo(@NotNull String url) {
    this(url, Conditions.alwaysTrue(), null);
  }

  public OpenUrlHyperlinkInfo(@NotNull String url, @Nullable WebBrowser browser) {
    this(url, Conditions.alwaysTrue(), browser);
  }

  public OpenUrlHyperlinkInfo(@NotNull String url, @NotNull Condition<? super WebBrowser> browserCondition) {
    this(url, browserCondition, null);
  }

  private OpenUrlHyperlinkInfo(@NotNull String url, @NotNull Condition<? super WebBrowser> browserCondition, @Nullable WebBrowser browser) {
    this.url = url;
    this.browserCondition = browserCondition;
    this.browser = browser;
  }

  @Override
  public ActionGroup getPopupMenuGroup(@NotNull MouseEvent event) {
    DefaultActionGroup group = new DefaultActionGroup();
    for (final WebBrowser browser : WebBrowserManager.getInstance().getActiveBrowsers()) {
      if (browserCondition.value(browser)) {
        group.add(new AnAction(IdeBundle.message("open.in.0", browser.getName()), IdeBundle.message("open.url.in.0", browser.getName()), browser.getIcon()) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            BrowserLauncher.getInstance().browse(url, browser, e.getProject());
          }
        });
      }
    }

    group.add(new AnAction(IdeBundle.messagePointer("action.OpenUrlHyperlinkInfo.Anonymous.text.copy.url"),
                           IdeBundle.messagePointer("action.OpenUrlHyperlinkInfo.Anonymous.description.copy.url.to.clipboard"),
                           PlatformIcons.COPY_ICON) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        CopyPasteManager.getInstance().setContents(new StringSelection(url));
      }
    });
    return group;
  }

  @Override
  public void navigate(@NotNull Project project) {
    BrowserLauncher.getInstance().browse(url, browser, project);
  }
}
