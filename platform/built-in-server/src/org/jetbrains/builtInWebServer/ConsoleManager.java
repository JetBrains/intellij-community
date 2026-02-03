// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.builtInWebServer;

import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class ConsoleManager {
  private ConsoleView console;

  public @NotNull ConsoleView getConsole(@NotNull NetService netService) {
    if (console == null) {
      createConsole(netService);
    }
    return console;
  }

  private void createConsole(final @NotNull NetService netService) {
    TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(netService.getProject());
    netService.configureConsole(consoleBuilder);
    console = consoleBuilder.getConsole();

    ApplicationManager.getApplication().invokeLater(() -> {
      ActionGroup actionGroup = netService.getConsoleToolWindowActions();
      ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("BuiltInServer", actionGroup, false);

      SimpleToolWindowPanel toolWindowPanel = new SimpleToolWindowPanel(false, true);
      toolWindowPanel.setContent(console.getComponent());
      toolWindowPanel.setToolbar(toolbar.getComponent());

      ToolWindow toolWindow = ToolWindowManager.getInstance(netService.getProject())
        .registerToolWindow(netService.getConsoleToolWindowId(), false, ToolWindowAnchor.BOTTOM, netService.getProject(), true);
      toolWindow.setIcon(netService.getConsoleToolWindowIcon());

      Content content = ContentFactory.getInstance().createContent(toolWindowPanel, "", false);
      Disposer.register(content, console);

      toolWindow.getContentManager().addContent(content);
    }, netService.getProject().getDisposed());
  }
}