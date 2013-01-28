/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.options.SharedScheme;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author max
 */
public class QuickChangeColorSchemeAction extends QuickSwitchSchemeAction {
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    final EditorColorsScheme[] schemes = EditorColorsManager.getInstance().getAllSchemes();
    EditorColorsScheme current = EditorColorsManager.getInstance().getGlobalScheme();
    for (final EditorColorsScheme scheme : schemes) {
      addScheme(group, current, scheme, false);
    }


    Collection<SharedScheme<EditorColorsSchemeImpl>> sharedSchemes = ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).getSchemesManager().loadSharedSchemes();

    if (!sharedSchemes.isEmpty()) {
      group.add(Separator.getInstance());

      for (SharedScheme<EditorColorsSchemeImpl> sharedScheme : sharedSchemes) {
        addScheme(group, current, sharedScheme.getScheme(), true);
      }
    }

  }

  private void addScheme(final DefaultActionGroup group, final EditorColorsScheme current, final EditorColorsScheme scheme, final boolean addScheme) {
    group.add(new AnAction(scheme.getName(), "", scheme == current ? ourCurrentAction : ourNotCurrentAction) {
      public void actionPerformed(AnActionEvent e) {
        if (addScheme) {
          EditorColorsManager.getInstance().addColorsScheme(scheme);
        }
        EditorColorsManager.getInstance().setGlobalScheme(scheme);
        Editor[] editors = EditorFactory.getInstance().getAllEditors();
        for (Editor editor : editors) {
          ((EditorEx)editor).reinitSettings();
        }
      }
    });
  }

  protected boolean isEnabled() {
    return EditorColorsManager.getInstance().getAllSchemes().length > 1 || ((EditorColorsManagerImpl)EditorColorsManager.getInstance()).getSchemesManager().isImportAvailable();
  }
}
