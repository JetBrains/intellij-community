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

package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.actions.MoveModulesOutsideGroupAction;
import com.intellij.ide.projectView.actions.MoveModulesToSubGroupAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MoveModuleToGroupTopLevel extends ActionGroup {
  @Override
  public void update(AnActionEvent e){
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    boolean active = project != null && modules != null && modules.length != 0;
    e.getPresentation().setVisible(active);
  }

  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) {
      return EMPTY_ARRAY;
    }
    List<String> topLevelGroupNames = new ArrayList<>(getTopLevelGroupNames(e.getDataContext()));
    Collections.sort ( topLevelGroupNames );

    List<AnAction> result = new ArrayList<>();
    result.add(new MoveModulesOutsideGroupAction());
    result.add(new MoveModulesToSubGroupAction(null));
    result.add(Separator.getInstance());
    for (String name : topLevelGroupNames) {
      result.add(new MoveModuleToGroup(new ModuleGroup(new String[]{name})));
    }
    return result.toArray(new AnAction[result.size()]);
  }

  private static Collection<String> getTopLevelGroupNames(final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);

    final ModifiableModuleModel model = LangDataKeys.MODIFIABLE_MODULE_MODEL.getData(dataContext);

    Module[] allModules;
    if ( model != null ) {
      allModules = model.getModules();
    } else {
      allModules = ModuleManager.getInstance(project).getModules();
    }

    Set<String> topLevelGroupNames = new HashSet<>();
    for (final Module child : allModules) {
      String[] group;
      if ( model != null ) {
        group = model.getModuleGroupPath(child);
      } else {
        group = ModuleManager.getInstance(project).getModuleGroupPath(child);
      }
      if (group != null) {
        topLevelGroupNames.add(group[0]);
      }
    }
    return topLevelGroupNames;
  }
}
