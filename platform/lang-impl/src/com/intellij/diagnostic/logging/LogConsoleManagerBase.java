/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.logging;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public abstract class LogConsoleManagerBase implements LogConsoleManager, Disposable {

  private final Project myProject;

  private final Map<AdditionalTabComponent, Content> myAdditionalContent = new HashMap<AdditionalTabComponent, Content>();

  private ExecutionEnvironment myEnvironment;
  private final GlobalSearchScope mySearchScope;

  /**
   * @deprecated use {@link #LogConsoleManagerBase(com.intellij.openapi.project.Project, com.intellij.psi.search.GlobalSearchScope)}
   */
  protected LogConsoleManagerBase(@NotNull Project project) {
    this(project, GlobalSearchScope.allScope(project));
  }

  protected LogConsoleManagerBase(@NotNull Project project, @NotNull GlobalSearchScope searchScope) {
    myProject = project;
    mySearchScope = searchScope;
  }

  protected final Project getProject() {
    return myProject;
  }

  public void setEnvironment(@NotNull final ExecutionEnvironment env) {
    myEnvironment = env;
  }

  protected final ExecutionEnvironment getEnvironment() {
    return myEnvironment;
  }

  @Override
  public void addLogConsole(final String name, final String path, @NotNull Charset charset, final long skippedContent) {
    addLogConsole(name, path, charset, skippedContent, getDefaultIcon());
  }

  public void addLogConsole(final String name, final String path, @NotNull Charset charset, final long skippedContent, Icon icon) {
    doAddLogConsole(new LogConsoleImpl(myProject, new File(path), charset, skippedContent, name, false, mySearchScope) {

      @Override
      public boolean isActive() {
        return isConsoleActive(path);
      }
    }, path, icon);
  }

  @Override
  public void addLogConsole(String name, Reader reader, final String id) {
    addLogConsole(name, reader, id, getDefaultIcon());
  }

  public void addLogConsole(String name, Reader reader, final String id, Icon icon) {
    doAddLogConsole(new LogConsoleBase(myProject,
                                       reader,
                                       name,
                                       false,
                                       new DefaultLogFilterModel(myProject), mySearchScope) {

      @Override
      public boolean isActive() {
        return isConsoleActive(id);
      }
    }, id, icon);
  }

  private void doAddLogConsole(final LogConsoleBase log,
                               final String id,
                               Icon icon) {
    if (myEnvironment != null && myEnvironment.getRunProfile() instanceof RunConfigurationBase) {
      ((RunConfigurationBase)myEnvironment.getRunProfile()).customizeLogConsole(log);
    }
    log.attachStopLogConsoleTrackingListener(getProcessHandler());
    addAdditionalTabComponent(log, id, icon);

    getUi().addListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(final ContentManagerEvent event) {
        log.activate();
      }
    }, log);
  }

  private boolean isConsoleActive(String id) {
    final Content content = getUi().findContent(id);
    return content != null && content.isSelected();
  }

  @Override
  public void removeLogConsole(final String path) {
    final Content content = getUi().findContent(path);
    if (content != null) {
      final LogConsoleBase log = (LogConsoleBase)content.getComponent();
      removeAdditionalTabComponent(log);
    }
  }

  @Override
  public void addAdditionalTabComponent(final AdditionalTabComponent tabComponent, final String id) {
    addAdditionalTabComponent(tabComponent, id, getDefaultIcon());
  }

  public Content addAdditionalTabComponent(final AdditionalTabComponent tabComponent, String id, Icon icon) {
    final Content logContent = createLogContent(tabComponent, id, icon);
    myAdditionalContent.put(tabComponent, logContent);
    getUi().addContent(logContent);
    return logContent;
  }

  protected Content createLogContent(AdditionalTabComponent tabComponent, String id, Icon icon) {
    return getUi().createContent(id, (ComponentWithActions)tabComponent, tabComponent.getTabTitle(), icon,
                                 tabComponent.getPreferredFocusableComponent());
  }

  @Override
  public void removeAdditionalTabComponent(AdditionalTabComponent component) {
    Disposer.dispose(component);
    final Content content = myAdditionalContent.remove(component);
    if (!getUi().isDisposed()) {
      getUi().removeContent(content, true);
    }
  }

  @Override
  public void dispose() {
    for (AdditionalTabComponent component : ArrayUtil.toObjectArray(myAdditionalContent.keySet(), AdditionalTabComponent.class)) {
      removeAdditionalTabComponent(component);
    }
  }

  protected abstract Icon getDefaultIcon();

  protected abstract RunnerLayoutUi getUi();

  public abstract ProcessHandler getProcessHandler();
}
