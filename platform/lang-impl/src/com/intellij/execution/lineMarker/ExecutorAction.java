/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.lineMarker;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.actions.*;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Key;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class ExecutorAction extends ActionGroup {
  private static final Key<List<ConfigurationFromContext>> CONFIGURATION_CACHE = Key.create("ConfigurationFromContext");

  @NotNull
  public static AnAction[] getActions(final int order) {
    return ContainerUtil.map2Array(ExecutorRegistry.getInstance().getRegisteredExecutors(), AnAction.class,
                                   (Function<Executor, AnAction>)executor -> new ExecutorAction(ActionManager.getInstance().getAction(executor.getContextActionId()), executor, order));
  }

  private final AnAction myOrigin;
  private final Executor myExecutor;
  private final int myOrder;

  private ExecutorAction(@NotNull AnAction origin,
                         @NotNull Executor executor,
                         int order) {
    myOrigin = origin;
    myExecutor = executor;
    myOrder = order;
    copyFrom(origin);
  }

  @Override
  public void update(AnActionEvent e) {
    String name = getActionName(e.getDataContext(), myExecutor);
    e.getPresentation().setVisible(name != null);
    myOrigin.update(e);
    e.getPresentation().setText(name);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myOrigin.actionPerformed(e);
  }

  @Override
  public boolean canBePerformed(DataContext context) {
    return !(myOrigin instanceof ActionGroup) || ((ActionGroup)myOrigin).canBePerformed(context);
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (myOrigin instanceof ActionGroup) {
      return ((ActionGroup)myOrigin).getChildren(e);
    }
    return AnAction.EMPTY_ARRAY;
  }

  @Override
  public boolean isDumbAware() {
    return myOrigin.isDumbAware();
  }

  @Override
  public boolean isPopup() {
    return !(myOrigin instanceof ActionGroup) || ((ActionGroup)myOrigin).isPopup();
  }

  @Override
  public boolean hideIfNoVisibleChildren() {
    return !(myOrigin instanceof ActionGroup) || ((ActionGroup)myOrigin).hideIfNoVisibleChildren();
  }

  @Override
  public boolean disableIfNoVisibleChildren() {
    return !(myOrigin instanceof ActionGroup) || ((ActionGroup)myOrigin).disableIfNoVisibleChildren();
  }

  @NotNull
  private static List<ConfigurationFromContext> getConfigurations(DataContext dataContext) {
    List<ConfigurationFromContext> result = DataManager.getInstance().loadFromDataContext(dataContext, CONFIGURATION_CACHE);
    if (result == null) {
      DataManager.getInstance().saveInDataContext(dataContext, CONFIGURATION_CACHE, result = calcConfigurations(dataContext));
    }
    return result;
  }

  @NotNull
  private static List<ConfigurationFromContext> calcConfigurations(DataContext dataContext) {
    final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
    if (context.getLocation() == null) return Collections.emptyList();
    List<RunConfigurationProducer<?>> producers = RunConfigurationProducer.getProducers(context.getProject());
    return ContainerUtil.mapNotNull(producers, producer -> createConfiguration(producer, context));
  }

  private String getActionName(DataContext dataContext, @NotNull Executor executor) {
    List<ConfigurationFromContext> list = getConfigurations(dataContext);
    if (list.isEmpty()) return null;
    ConfigurationFromContext configuration = list.get(myOrder < list.size() ? myOrder : 0);
    String actionName = BaseRunConfigurationAction.suggestRunActionName((LocatableConfiguration)configuration.getConfiguration());
    return executor.getStartActionText(actionName);
  }

  @Nullable
  private static ConfigurationFromContext createConfiguration(RunConfigurationProducer<?> producer,
                                                              ConfigurationContext context) {
    RunConfiguration configuration = producer.createLightConfiguration(context);
    if (configuration == null) return null;
    RunnerAndConfigurationSettingsImpl
      settings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(context.getProject()), configuration, false);
    return new ConfigurationFromContextImpl(producer, settings, context.getPsiLocation());
  }
}
