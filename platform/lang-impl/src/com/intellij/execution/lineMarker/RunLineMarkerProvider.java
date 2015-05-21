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
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionPopupMenuImpl;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Getter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

public class RunLineMarkerProvider implements LineMarkerProvider {

  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull final PsiElement element) {
    if (!(element instanceof PsiNameIdentifierOwner)) {
      return null;
    }
    ConfigurationContext context = new ConfigurationContext(element);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    if (configurations != null && !configurations.isEmpty()) {
      configurations = ContainerUtil.filter(configurations, new Condition<ConfigurationFromContext>() {
        @Override
        public boolean value(ConfigurationFromContext context) {
          RunConfiguration configuration = context.getConfiguration();
          if (!(configuration instanceof RunConfigurationBase)) return false;
          return ((RunConfigurationBase)configuration).isLineMarkerPlace(element, context.getSourceElement());
        }
      });
      if (configurations.isEmpty()) return null;
      Icon icon = null;
      for (ConfigurationFromContext configuration : configurations) {
        icon = configuration.getConfiguration().getType().getIcon();
      }
      return new LineMarkerInfo<PsiElement>(element, element.getTextOffset(), icon, Pass.UPDATE_ALL, null,
                                            null, GutterIconRenderer.Alignment.CENTER) {
        @Nullable
        @Override
        public GutterIconRenderer createGutterRenderer() {
          return new LineMarkerGutterIconRenderer<PsiElement>(this) {
            @Override
            public boolean isNavigateAction() {
              return true;
            }

            @Override
            public AnAction getClickAction() {
              return new AnAction() {
                @Override
                public void actionPerformed(AnActionEvent e) {
                  Executor[] executors = ExecutorRegistry.getInstance().getRegisteredExecutors();
                  List<AnAction> actions = ContainerUtil.mapNotNull(executors,
                                                                    new Function<Executor, AnAction>() {
                                                                      @Override
                                                                      public AnAction fun(Executor executor) {
                                                                        return ActionManager.getInstance().getAction(executor.getContextActionId());
                                                                      }
                                                                    });
                  ActionPopupMenuImpl popupMenu = (ActionPopupMenuImpl)ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, new DefaultActionGroup(actions));

                  final MouseEvent me = (MouseEvent)e.getInputEvent();
                  final Component c = me.getComponent();
                  if (c != null && c.isShowing()) {
                    popupMenu.setDataContextProvider(new Getter<DataContext>() {
                      @Override
                      public DataContext get() {
                        final DataContext delegate = DataManager.getInstance().getDataContext(c, me.getX(), me.getY());
                        return new DataContext() {
                          @Nullable
                          @Override
                          public Object getData(@NonNls String dataId) {
                            if (Location.DATA_KEY.is(dataId))  return new PsiLocation<PsiElement>(element);
                            return delegate.getData(dataId);
                          }
                        };
                      }
                    });
                    popupMenu.getComponent().show(c, me.getX(), me.getY());
                  }
                }
              };
            }
          };
        }
      };
    }
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
  }
}
