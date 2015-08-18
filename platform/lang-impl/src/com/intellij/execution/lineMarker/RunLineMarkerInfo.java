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

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.*;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class RunLineMarkerInfo extends LineMarkerInfo<PsiElement> {
  private final PsiElement myElement;

  public RunLineMarkerInfo(PsiElement element, Icon icon, Function<? super PsiElement, String> tooltipProvider) {
    super(element, element.getTextOffset(), icon, Pass.UPDATE_ALL, tooltipProvider, null, GutterIconRenderer.Alignment.CENTER);
    myElement = element;
  }

  @Nullable
  @Override
  public GutterIconRenderer createGutterRenderer() {
    return new LineMarkerGutterIconRenderer<PsiElement>(this) {
      @Override
      public boolean isNavigateAction() {
        return true;
      }

      @NotNull
      @Override
      public ActionGroup getPopupMenuActions() {
        List<AnAction> actions = new ArrayList<AnAction>();
        Executor[] executors = ExecutorRegistry.getInstance().getRegisteredExecutors();
        actions.addAll(ContainerUtil.mapNotNull(executors,
                                                new Function<Executor, AnAction>() {
                                                  @Override
                                                  public AnAction fun(Executor executor) {
                                                    return new ActionWrapper(ActionManager.getInstance().getAction(executor.getContextActionId()), executor);
                                                  }
                                                }));
        actions.add(Separator.getInstance());
        actions.addAll(ContainerUtil.mapNotNull(RunLineMarkerContributor.EXTENSION.allForLanguage(myElement.getLanguage()),
                                                new NullableFunction<RunLineMarkerContributor, AnAction>() {
                                                  @Nullable
                                                  @Override
                                                  public AnAction fun(RunLineMarkerContributor contributor) {
                                                    AnAction action = contributor.getAdditionalAction(myElement);
                                                    return action != null ? new ActionWrapper(action, null) : null;
                                                  }
                                                }));

        return new DefaultActionGroup(actions);
      }

      @Override
      public AnAction getClickAction() {
        return null;
      }
    };
  }

  private class ActionWrapper extends AnAction {

    private final AnAction myOrigin;
    @Nullable
    private final Executor myExecutor;

    public ActionWrapper(@NotNull AnAction origin, @Nullable Executor executor) {
      myOrigin = origin;
      myExecutor = executor;
      copyFrom(origin);
    }

    @Override
    public void update(AnActionEvent e) {
      AnActionEvent event = wrapEvent(e);
      if (myExecutor != null) {
        String name = getActionName(event.getDataContext(), myExecutor);
        e.getPresentation().setVisible(name != null);
        e.getPresentation().setText(name);
      }
      else {
        myOrigin.update(event);
      }
    }

    @NotNull
    private AnActionEvent wrapEvent(AnActionEvent e) {
      return new AnActionEvent(
        e.getInputEvent(), new MyDataContext(e.getDataContext()), e.getPlace(), e.getPresentation(), e.getActionManager(), e.getModifiers());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myOrigin.actionPerformed(wrapEvent(e));
    }
  }
  
  private class MyDataContext extends UserDataHolderBase implements DataContext {
    private final DataContext myDelegate;

    public MyDataContext(DataContext delegate) {
      myDelegate = delegate;
    }

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      if (Location.DATA_KEY.is(dataId)) return myElement.isValid() ? new PsiLocation<PsiElement>(myElement) : null;
      return myDelegate.getData(dataId);
    }
  }

  public String getActionName(DataContext dataContext, @NotNull Executor executor) {
    final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
    List<RunConfigurationProducer<?>> producers = RunConfigurationProducer.getProducers(context.getProject());
    List<ConfigurationFromContext> list = ContainerUtil.mapNotNull(producers,
                                                                   new Function<RunConfigurationProducer<?>, ConfigurationFromContext>() {
                                                                     @Override
                                                                     public ConfigurationFromContext fun(RunConfigurationProducer<?> producer) {
                                                                       return createConfiguration(producer, context);
                                                                     }
                                                                   }
    );
    if (list.isEmpty()) return null;
    Collections.sort(list, ConfigurationFromContext.COMPARATOR);
    String actionName = BaseRunConfigurationAction.suggestRunActionName((LocatableConfiguration)list.get(0).getConfiguration());
    return executor.getStartActionText(actionName);
  }

  @Nullable
  private ConfigurationFromContext createConfiguration(RunConfigurationProducer<?> producer, ConfigurationContext context) {
    RunConfiguration configuration = producer.createLightConfiguration(context);
    if (configuration == null) return null;
    RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(context.getProject()), configuration, false);
    return new ConfigurationFromContextImpl(producer, settings, getElement());
  }
}
