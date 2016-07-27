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

import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.QuickFixAction;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.ui.actions.InspectionViewActionBase.getView;

/**
 * @author Dmitry Batkovich
 */
public class QuickFixesViewActionGroup extends ActionGroup {
  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    final InspectionResultsView view = getView(e);
    if (view == null || InvokeQuickFixAction.cantApplyFixes(view)) {
      return AnAction.EMPTY_ARRAY;
    }
    InspectionToolWrapper toolWrapper = view.getTree().getSelectedToolWrapper(true);
    if (toolWrapper == null) return AnAction.EMPTY_ARRAY;
    final QuickFixAction[] quickFixes = view.getProvider().getQuickFixes(toolWrapper, view.getTree());
    if (quickFixes == null || quickFixes.length == 0) {
      return AnAction.EMPTY_ARRAY;
    }
    return quickFixes;
  }
}
