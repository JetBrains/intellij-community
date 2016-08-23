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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.actions.MoveModulesToGroupAction;
import com.intellij.ide.projectView.actions.MoveModulesToSubGroupAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MoveModuleToGroup extends ActionGroup {
  private final ModuleGroup myModuleGroup;

  public MoveModuleToGroup(ModuleGroup moduleGroup) {
    myModuleGroup = moduleGroup;
    setPopup(true);
  }

  @Override
  public void update(AnActionEvent e){
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    boolean active = project != null && modules != null && modules.length != 0;
    final Presentation presentation = e.getPresentation();
    presentation.setVisible(active);
    presentation.setText(myModuleGroup.presentableText());
  }

  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;

    List<ModuleGroup> children = new ArrayList<>(myModuleGroup.childGroups(e.getDataContext()));
    Collections.sort (children, (moduleGroup1, moduleGroup2) -> {
      assert moduleGroup1.getGroupPath().length == moduleGroup2.getGroupPath().length;
      return moduleGroup1.toString().compareToIgnoreCase(moduleGroup2.toString());
    });

    List<AnAction> result = new ArrayList<>();
    result.add(new MoveModulesToGroupAction(myModuleGroup, IdeBundle.message("action.move.module.to.this.group")));
    result.add(new MoveModulesToSubGroupAction(myModuleGroup));
     result.add(Separator.getInstance());
    for (final ModuleGroup child : children) {
      result.add(new MoveModuleToGroup(child));
    }

    return result.toArray(new AnAction[result.size()]);
  }
}
