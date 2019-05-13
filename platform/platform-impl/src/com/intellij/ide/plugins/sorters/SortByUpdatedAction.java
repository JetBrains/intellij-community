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

import com.intellij.ide.plugins.PluginTable;
import com.intellij.ide.plugins.PluginTableModel;

/**
 * @author Konstantin Bulenkov
 */
public class SortByUpdatedAction extends AbstractSortByAction {
  public SortByUpdatedAction(PluginTable table, PluginTableModel model) {
    super("Last Updated", table, model);
  }

  @Override
  public boolean isSelected() {
    return myModel.isSortByUpdated();
  }

  @Override
  protected void setSelected(boolean state) {
    myModel.setSortByUpdated(state);
  }
}
