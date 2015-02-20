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
import com.intellij.openapi.project.DumbAware;

/**
 * @author Konstantin Bulenkov
 */
public abstract class AbstractSortByAction extends ToggleAction implements DumbAware {
  protected final PluginTable myTable;
  protected final PluginTableModel myModel;

  public AbstractSortByAction(String name, PluginTable table, PluginTableModel model) {
    super(name, name, null);
    myTable = table;
    myModel = model;
  }

  public abstract boolean isSelected();

  protected abstract void setSelected(boolean state);

  @Override
  public final boolean isSelected(AnActionEvent e) {
    return isSelected();
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  @Override
  public final void setSelected(AnActionEvent e, boolean state) {
    IdeaPluginDescriptor[] selected = myTable.getSelectedObjects();
    setSelected(state);
    myModel.sort();
    if (selected != null) {
      myTable.select(selected);
    }
  }
}
