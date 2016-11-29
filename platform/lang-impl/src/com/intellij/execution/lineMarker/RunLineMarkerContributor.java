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

import com.intellij.ide.DataManager;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class RunLineMarkerContributor {
  static final LanguageExtension<RunLineMarkerContributor> EXTENSION = new LanguageExtension<>("com.intellij.runLineMarkerContributor");

  public static class Info {
    public final Icon icon;
    public final AnAction[] actions;
    public final Function<PsiElement, String> tooltipProvider;

    public Info(Icon icon, @Nullable Function<PsiElement, String> tooltipProvider, @NotNull AnAction... actions) {
      this.icon = icon;
      this.actions = actions;
      this.tooltipProvider = tooltipProvider;
    }

    public Info(@NotNull final AnAction action) {
      this(action.getTemplatePresentation().getIcon(), element -> getText(action, element), action);
    }
  }

  @Nullable
  public abstract Info getInfo(PsiElement element);

  @Nullable("null means disabled")
  protected static String getText(@NotNull AnAction action, @NotNull PsiElement element) {
    DataContext parent = DataManager.getInstance().getDataContext();
    DataContext dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PSI_ELEMENT.getName(), element, parent);
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.STATUS_BAR_PLACE, dataContext);
    action.update(event);
    Presentation presentation = event.getPresentation();
    return presentation.isEnabled() && presentation.isVisible() ? presentation.getText() : null;
  }

}
