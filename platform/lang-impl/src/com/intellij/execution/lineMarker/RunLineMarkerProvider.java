/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class RunLineMarkerProvider extends LineMarkerProviderDescriptor {

  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    List<RunLineMarkerContributor> contributors = RunLineMarkerContributor.EXTENSION.allForLanguage(element.getLanguage());
    DefaultActionGroup actionGroup = null;
    Icon icon = null;
    final List<RunLineMarkerContributor.Info> infos = new ArrayList<>();
    for (RunLineMarkerContributor contributor : contributors) {
      RunLineMarkerContributor.Info info = contributor.getInfo(element);
      if (info == null) {
        continue;
      }
      if (icon == null) {
        icon = info.icon;
      }
      if (actionGroup == null) {
        actionGroup = new DefaultActionGroup();
      }
      infos.add(info);
      for (AnAction action : info.actions) {
        actionGroup.add(new LineMarkerActionWrapper(element, action));
      }
      actionGroup.add(new Separator());
    }
    if (icon == null) return null;

    final DefaultActionGroup finalActionGroup = actionGroup;
    Function<PsiElement, String> tooltipProvider = element1 -> {
      final StringBuilder tooltip = new StringBuilder();
      for (RunLineMarkerContributor.Info info : infos) {
        if (info.tooltipProvider != null) {
          String string = info.tooltipProvider.fun(element1);
          if (string == null) continue;
          if (tooltip.length() != 0) {
            tooltip.append("\n");
          }
          tooltip.append(string);
        }
      }

      return tooltip.length() == 0 ? null : tooltip.toString();
    };
    return new LineMarkerInfo<PsiElement>(element, element.getTextRange(), icon, Pass.LINE_MARKERS,
                                          tooltipProvider, null,
                                          GutterIconRenderer.Alignment.CENTER) {
      @Nullable
      @Override
      public GutterIconRenderer createGutterRenderer() {
        return new LineMarkerGutterIconRenderer<PsiElement>(this) {
          @Override
          public AnAction getClickAction() {
            return null;
          }

          @Override
          public boolean isNavigateAction() {
            return true;
          }

          @Nullable
          @Override
          public ActionGroup getPopupMenuActions() {
            return finalActionGroup;
          }
        };
      }
    };
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
  }

  @NotNull
  @Override
  public String getName() {
    return "Run line marker";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.RunConfigurations.TestState.Run;
  }
}