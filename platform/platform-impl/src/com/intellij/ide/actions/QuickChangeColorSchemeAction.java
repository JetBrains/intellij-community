/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class QuickChangeColorSchemeAction extends QuickSwitchSchemeAction {
  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    EditorColorsScheme current = EditorColorsManager.getInstance().getGlobalScheme();
    for (EditorColorsScheme scheme : EditorColorsManager.getInstance().getAllSchemes()) {
      addScheme(group, current, scheme, false);
    }
  }

  private static void addScheme(final DefaultActionGroup group,
                                final EditorColorsScheme current,
                                final EditorColorsScheme scheme,
                                final boolean addScheme) {
    group.add(new DumbAwareAction(scheme.getName(), "", scheme == current ? ourCurrentAction : ourNotCurrentAction) {
      @Override
      public void actionPerformed(@Nullable AnActionEvent e) {
        if (addScheme) {
          EditorColorsManager.getInstance().addColorsScheme(scheme);
        }
        EditorColorsManager.getInstance().setGlobalScheme(scheme);
      }
    });
  }

  @Override
  protected boolean isEnabled() {
    return EditorColorsManager.getInstance().getAllSchemes().length > 1;
  }
}
