/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.startup;

import com.intellij.execution.RunnerAndConfigurationSettings;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.List;

/**
 * @author Irina.Chernushina on 8/19/2015.
 */
public class ProjectStartupTasksTreeModel implements TreeModel {
  private final Object myRoot = new Object();
  private final List<RunnerAndConfigurationSettings> myConfigurations;

  public ProjectStartupTasksTreeModel(List<RunnerAndConfigurationSettings> configurations) {
    myConfigurations = configurations;
  }

  @Override
  public Object getRoot() {
    return myRoot;
  }

  @Override
  public Object getChild(Object parent, int index) {
    if (parent == myRoot) {
      return myConfigurations.get(index);
    }
    return null;
  }

  @Override
  public int getChildCount(Object parent) {
    if (parent == myRoot) {
      return myConfigurations.size();
    }
    return 0;
  }

  @Override
  public boolean isLeaf(Object node) {
    return getChildCount(node) == 0;
  }

  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
  }

  @Override
  public int getIndexOfChild(Object parent, Object child) {
    if (parent == myRoot) {
      for (int i = 0; i < myConfigurations.size(); i++) {
        final RunnerAndConfigurationSettings configuration = myConfigurations.get(i);
        if (configuration == child) {
          return i;
        }
      }
    }
    return 0;
  }

  @Override
  public void addTreeModelListener(TreeModelListener l) {

  }

  @Override
  public void removeTreeModelListener(TreeModelListener l) {

  }

  public List<RunnerAndConfigurationSettings> getConfigurations() {
    return myConfigurations;
  }
}
