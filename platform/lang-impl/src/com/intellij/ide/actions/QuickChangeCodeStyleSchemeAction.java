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
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.options.SharedScheme;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemesImpl;

import java.util.Collection;

/**
 * @author max
 */
public class QuickChangeCodeStyleSchemeAction extends QuickSwitchSchemeAction {
  protected void fillActions(Project project, DefaultActionGroup group, DataContext dataContext) {
    final CodeStyleSettingsManager manager = CodeStyleSettingsManager.getInstance(project);
    if (manager.PER_PROJECT_SETTINGS != null) {
      //noinspection HardCodedStringLiteral
      group.add(new AnAction("<project>", "",
                             manager.USE_PER_PROJECT_SETTINGS ? ourCurrentAction : ourNotCurrentAction) {
        public void actionPerformed(AnActionEvent e) {
          manager.USE_PER_PROJECT_SETTINGS = true;
        }
      });
    }

    final CodeStyleScheme[] schemes = CodeStyleSchemes.getInstance().getSchemes();
    final CodeStyleScheme currentScheme = CodeStyleSchemes.getInstance().getCurrentScheme();

    for (final CodeStyleScheme scheme : schemes) {
      addScheme(group, manager, currentScheme, scheme, false);
    }


    Collection<SharedScheme<CodeStyleSchemeImpl>> sharedSchemes =
        ((CodeStyleSchemesImpl)CodeStyleSchemes.getInstance()).getSchemesManager().loadSharedSchemes();
    if (!sharedSchemes.isEmpty()) {
      group.add(Separator.getInstance());


      for (SharedScheme<CodeStyleSchemeImpl> scheme : sharedSchemes) {
        addScheme(group, manager, currentScheme, scheme.getScheme(), true);
      }
    }
  }

  private static void addScheme(final DefaultActionGroup group,
                                final CodeStyleSettingsManager manager,
                                final CodeStyleScheme currentScheme,
                                final CodeStyleScheme scheme,
                                final boolean addScheme) {
    group.add(new AnAction(scheme.getName(), "",
                           scheme == currentScheme && !manager.USE_PER_PROJECT_SETTINGS ? ourCurrentAction : ourNotCurrentAction) {
      public void actionPerformed(AnActionEvent e) {
        if (addScheme) {
          CodeStyleSchemes.getInstance().addScheme(scheme);
        }
        CodeStyleSchemes.getInstance().setCurrentScheme(scheme);
        manager.USE_PER_PROJECT_SETTINGS = false;
        EditorFactory.getInstance().refreshAllEditors();
      }
    });
  }

  protected boolean isEnabled() {
    return CodeStyleSchemes.getInstance().getSchemes().length > 1;
  }

  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(PlatformDataKeys.PROJECT.getData(e.getDataContext()) != null);
  }
}
