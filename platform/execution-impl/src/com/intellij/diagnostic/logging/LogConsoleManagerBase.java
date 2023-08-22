// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logging;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.UIExperiment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public abstract class LogConsoleManagerBase implements LogConsoleManager, Disposable {
  private final Project myProject;
  private final Map<AdditionalTabComponent, Content> myAdditionalContent = new HashMap<>();
  private final GlobalSearchScope mySearchScope;

  protected LogConsoleManagerBase(@NotNull Project project, @NotNull GlobalSearchScope searchScope) {
    myProject = project;
    mySearchScope = searchScope;
  }

  @Override
  public void addLogConsole(@NlsContexts.TabTitle @NotNull final String name,
                            @NotNull final String path,
                            @NotNull Charset charset,
                            final long skippedContent,
                            @NotNull RunConfigurationBase runConfiguration) {
    boolean useBuildInActions = UIExperiment.isNewDebuggerUIEnabled();
    doAddLogConsole(new LogConsoleImpl(myProject, new File(path), charset, skippedContent, name, useBuildInActions, mySearchScope) {
      @Override
      public boolean isActive() {
        return isConsoleActive(path);
      }
    }, path, getDefaultIcon(), runConfiguration);
  }

  private void doAddLogConsole(@NotNull final LogConsoleBase log, String id, Icon icon, @Nullable RunProfile runProfile) {
    if (runProfile instanceof RunConfigurationBase) {
      ((RunConfigurationBase<?>)runProfile).customizeLogConsole(log);
    }
    log.attachStopLogConsoleTrackingListener(getProcessHandler());
    addAdditionalTabComponent(log, id, icon);

    getUi().addListener(new ContentManagerListener() {
      @Override
      public void selectionChanged(@NotNull final ContentManagerEvent event) {
        log.activate();
      }
    }, log);
  }

  private boolean isConsoleActive(String id) {
    final Content content = getUi().findContent(id);
    return content != null && content.isSelected();
  }

  @Override
  public void removeLogConsole(@NotNull String path) {
    Content content = getUi().findContent(path);
    if (content != null) {
      removeAdditionalTabComponent((LogConsoleBase)content.getComponent());
    }
  }

  @Override
  public void addAdditionalTabComponent(@NotNull AdditionalTabComponent tabComponent, @NotNull String id) {
    addAdditionalTabComponent(tabComponent, id, getDefaultIcon());
  }

  public Content addAdditionalTabComponent(@NotNull AdditionalTabComponent tabComponent, @NotNull String id, @Nullable Icon icon) {
    return addAdditionalTabComponent(tabComponent, id, icon, true);
  }

  public Content addAdditionalTabComponent(@NotNull AdditionalTabComponent tabComponent,
                                           @NotNull String id,
                                           @Nullable Icon icon,
                                           boolean closeable) {
    Content logContent = getUi().createContent(id, (ComponentWithActions)tabComponent, tabComponent.getTabTitle(), icon,
                                               tabComponent.getPreferredFocusableComponent());
    logContent.setCloseable(closeable);
    myAdditionalContent.put(tabComponent, logContent);
    getUi().addContent(logContent);
    return logContent;
  }

  @Override
  public void removeAdditionalTabComponent(@NotNull AdditionalTabComponent component) {
    Disposer.dispose(component);
    final Content content = myAdditionalContent.remove(component);
    if (!getUi().isDisposed()) {
      getUi().removeContent(content, true);
    }
  }

  @Override
  public void dispose() {
    for (AdditionalTabComponent component : myAdditionalContent.keySet().toArray(new AdditionalTabComponent[0])) {
      removeAdditionalTabComponent(component);
    }
  }

  protected abstract Icon getDefaultIcon();

  protected abstract RunnerLayoutUi getUi();

  public abstract ProcessHandler getProcessHandler();
}
