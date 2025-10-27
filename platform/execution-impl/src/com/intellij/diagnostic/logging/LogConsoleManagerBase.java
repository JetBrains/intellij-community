// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logging;

import com.intellij.execution.configurations.AdditionalTabComponentManagerEx;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.UIExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.nio.charset.Charset;

public abstract class LogConsoleManagerBase implements LogConsoleManager, AdditionalTabComponentManagerEx {
  private final Project myProject;
  private final GlobalSearchScope mySearchScope;
  private final AdditionalTabComponentManagerEx myDelegate;

  protected LogConsoleManagerBase(@NotNull Project project, @NotNull GlobalSearchScope searchScope) {
    this(project, searchScope, null);
  }

  @ApiStatus.Internal
  protected LogConsoleManagerBase(@NotNull Project project, @NotNull GlobalSearchScope searchScope,
                                  @Nullable AdditionalTabComponentManagerEx delegate) {
    myProject = project;
    mySearchScope = searchScope;
    if (delegate != null) {
      myDelegate = delegate;
    }
    else {
      //noinspection AbstractMethodCallInConstructor
      myDelegate = new AdditionalTabComponentManagerImpl(getUi(), getDefaultIcon());
    }
  }

  @Override
  public void addLogConsole(final @NlsContexts.TabTitle @NotNull String name,
                            final @NotNull String path,
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

  private void doAddLogConsole(final @NotNull LogConsoleBase log, String id, Icon icon, @Nullable RunProfile runProfile) {
    if (runProfile instanceof RunConfigurationBase) {
      ((RunConfigurationBase<?>)runProfile).customizeLogConsole(log);
    }
    log.attachStopLogConsoleTrackingListener(getProcessHandler());
    addAdditionalTabComponent(log, id, icon);

    getUi().addListener(new ContentManagerListener() {
      @Override
      public void selectionChanged(final @NotNull ContentManagerEvent event) {
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
    myDelegate.addAdditionalTabComponent(tabComponent, id);
  }

  public Content addAdditionalTabComponent(@NotNull AdditionalTabComponent tabComponent, @NotNull String id, @Nullable Icon icon) {
    return addAdditionalTabComponent(tabComponent, id, icon, true);
  }

  @Override
  public @Nullable Content addAdditionalTabComponent(
    @NotNull AdditionalTabComponent tabComponent,
    @NotNull String id,
    @Nullable Icon icon,
    boolean closeable
  ) {
    return myDelegate.addAdditionalTabComponent(tabComponent, id, icon, closeable);
  }

  @Override
  public void removeAdditionalTabComponent(@NotNull AdditionalTabComponent component) {
    myDelegate.removeAdditionalTabComponent(component);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDelegate);
  }

  protected abstract Icon getDefaultIcon();

  protected abstract RunnerLayoutUi getUi();

  public abstract ProcessHandler getProcessHandler();
}
