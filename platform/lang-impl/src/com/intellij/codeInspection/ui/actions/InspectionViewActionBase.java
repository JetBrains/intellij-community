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
package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Batkovich
 */
public abstract class InspectionViewActionBase extends AnAction {
  public InspectionViewActionBase(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  public InspectionViewActionBase(String name) {
    super(name);
  }

  @Override
  public final void update(AnActionEvent e) {
    final InspectionResultsView view = getView(e);
    final boolean enabled = view != null && isEnabled(view, e);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(enabled);
  }

  protected boolean isEnabled(@NotNull InspectionResultsView view, AnActionEvent e) {
    return true;
  }

  public static InspectionResultsView getView(@Nullable AnActionEvent event) {
    if (event == null) {
      return null;
    }
    final InspectionResultsView view = InspectionResultsView.DATA_KEY.getData(event.getDataContext());
    return view == null || view.isDisposed() ? null : view;
  }
}
