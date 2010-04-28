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

package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.structureView.FileEditorPositionListener;
import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.TreeAction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class TreeModelWrapper implements StructureViewModel {
  private final StructureViewModel myModel;
  private final TreeActionsOwner myStructureView;

  public TreeModelWrapper(StructureViewModel model, TreeActionsOwner structureView) {
    myModel = model;
    myStructureView = structureView;
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    return myModel.getRoot();
  }

  @NotNull
  public Grouper[] getGroupers() {
    ArrayList<TreeAction> filtered = filterActive(myModel.getGroupers());
    return filtered.toArray(new Grouper[filtered.size()]);
  }

  private ArrayList<TreeAction> filterActive(TreeAction[] actions) {
    ArrayList<TreeAction> filtered = new ArrayList<TreeAction>();
    for (TreeAction action : actions) {
      if (action instanceof Sorter && !((Sorter)action).isVisible() || myStructureView.isActionActive(action.getName())) filtered.add(action);
    }
    return filtered;
  }

  @NotNull
  public Sorter[] getSorters() {
    ArrayList<TreeAction> filtered = filterActive(myModel.getSorters());
    return filtered.toArray(new Sorter[filtered.size()]);
  }

  @NotNull
  public Filter[] getFilters() {
    ArrayList<TreeAction> filtered = filterActive(myModel.getFilters());
    return filtered.toArray(new Filter[filtered.size()]);
  }

  public Object getCurrentEditorElement() {
    return myModel.getCurrentEditorElement();
  }

  public static boolean isActive(final TreeAction action, final TreeActionsOwner actionsOwner) {
    if (shouldRevert(action)) {
      return !actionsOwner.isActionActive(action.getName());
    }
    else {
      if (action instanceof Sorter && !((Sorter)action).isVisible()) return true;
      return actionsOwner.isActionActive(action.getName());
    }
  }

  public static boolean shouldRevert(final TreeAction action) {
    return action instanceof Filter && ((Filter)action).isReverted();
  }

  public void addEditorPositionListener(FileEditorPositionListener listener) {
    myModel.addEditorPositionListener(listener);
  }

  public void removeEditorPositionListener(FileEditorPositionListener listener) {
    myModel.removeEditorPositionListener(listener);
  }

  public void dispose() {
    myModel.dispose();
  }

  public boolean shouldEnterElement(final Object element) {
    return false;
  }

  public void addModelListener(ModelListener modelListener) {
    myModel.addModelListener(modelListener);
  }

  public void removeModelListener(ModelListener modelListener) {
    myModel.removeModelListener(modelListener);
  }

  public StructureViewModel getModel() {
    return myModel;
  }
}
