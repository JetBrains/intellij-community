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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
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
                                                    return ActionManager.getInstance().getAction(executor.getContextActionId());
                                                  }
                                                }));
        actions.add(Separator.getInstance());
        actions.addAll(ContainerUtil.mapNotNull(RunLineMarkerContributor.EXTENSION.allForLanguage(myElement.getLanguage()),
                                                new NullableFunction<RunLineMarkerContributor, AnAction>() {
                                                  @Nullable
                                                  @Override
                                                  public AnAction fun(RunLineMarkerContributor contributor) {
                                                    return contributor.getAdditionalAction(myElement);
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
}
