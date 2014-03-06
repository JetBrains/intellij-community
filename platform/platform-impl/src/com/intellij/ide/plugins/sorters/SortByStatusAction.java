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
package com.intellij.ide.plugins.sorters;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginTable;
import com.intellij.ide.plugins.PluginTableModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;

/**
* @author Konstantin Bulenkov
*/
public class SortByStatusAction extends ToggleAction {
  private final PluginTable myTable;
  private final PluginTableModel myModel;

  public SortByStatusAction(PluginTable pluginTable, PluginTableModel pluginsModel) {
    super("Status", "Status", null);
    myTable = pluginTable;
    myModel = pluginsModel;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myModel.isSortByStatus();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    IdeaPluginDescriptor[] selected = myTable.getSelectedObjects();
    myModel.setSortByStatus(state);
    myModel.sort();
    if (selected != null) {
      myTable.select(selected);
    }
  }
}
