/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.model.*;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementSettingsGrouper;
import com.intellij.util.containers.hash.HashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Set;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/15/12 2:40 PM
 */
public class ArrangementRuleEditingModelImpl implements ArrangementRuleEditingModel {

  @NotNull private static final MyConditionsBuilder CONDITIONS_BUILDER = new MyConditionsBuilder();

  @NotNull private final Set<Listener> myListeners  = new HashSet<Listener>();
  @NotNull private final Set<Object>   myConditions = new HashSet<Object>();

  @NotNull private final TIntObjectHashMap<ArrangementRuleEditingModelImpl> myRowMappings;
  @NotNull private final ArrangementSettingsGrouper                         myGrouper;

  @NotNull private DefaultMutableTreeNode  myTopMost;
  @NotNull private DefaultMutableTreeNode  myBottomMost;
  @NotNull private ArrangementSettingsNode mySettingsNode;
  private          int                     myRow;

  /**
   * Creates new <code>ArrangementRuleEditingModelImpl</code> object.
   *
   * @param node       backing settings node
   * @param topMost    there is a possible case that a single settings node is shown in more than one visual line
   *                   ({@link HierarchicalArrangementSettingsNode}). This argument is the top-most UI node used for the
   *                   settings node representation
   * @param bottomMost bottom-most UI node used for the given settings node representation 
   * @param grouper    strategy that encapsulates information on how settings node should be displayed
   * @param mappings   {@code 'row -> model'} mappings
   * @param row        row number for which current model is registered at the given model mappings
   */
  public ArrangementRuleEditingModelImpl(@NotNull ArrangementSettingsNode node,
                                         @NotNull DefaultMutableTreeNode topMost,
                                         @NotNull DefaultMutableTreeNode bottomMost,
                                         @NotNull ArrangementSettingsGrouper grouper,
                                         @NotNull TIntObjectHashMap<ArrangementRuleEditingModelImpl> mappings,
                                         int row)
  {
    mySettingsNode = node;
    myTopMost = topMost;
    myBottomMost = bottomMost;
    myGrouper = grouper;
    myRowMappings = mappings;
    myRow = row;
    refreshConditions();
  }

  private void refreshConditions() {
    myConditions.clear();
    CONDITIONS_BUILDER.conditions = myConditions;
    try {
      mySettingsNode.invite(CONDITIONS_BUILDER);
    }
    finally {
      CONDITIONS_BUILDER.conditions = null;
    }
  }

  @NotNull
  @Override
  public ArrangementSettingsNode getSettingsNode() {
    return mySettingsNode;
  }

  @NotNull
  public DefaultMutableTreeNode getTopMost() {
    return myTopMost;
  }

  @NotNull
  public DefaultMutableTreeNode getBottomMost() {
    return myBottomMost;
  }

  @Override
  public boolean hasCondition(@NotNull Object key) {
    return myConditions.contains(key);
  }

  /**
   * There is a possible case that tree nodes referenced by the current model become out of date due to a tree modification.
   * <p/>
   * This method asks the model to refresh its tree nodes if necessary.
   */
  public void refreshTreeNodes() {
    for (DefaultMutableTreeNode node = myBottomMost; node != null; node = (DefaultMutableTreeNode)node.getParent()) {
      if (node == myTopMost) {
        // No refresh is necessary.
        return;
      }
      else if (myTopMost.getUserObject().equals(node.getUserObject())) {
        myTopMost = node;
        return;
      }
    }
    assert false;
  }
  
  @Override
  public void addAndCondition(@NotNull ArrangementSettingsAtomNode node) {
    doAddAndCondition(node);
    refreshConditions();
    notifyListeners();
  }
  
  private void doAddAndCondition(@NotNull ArrangementSettingsAtomNode node) {
    ArrangementSettingsNode newNode = ArrangementUtil.and(mySettingsNode.clone(), node);
    applyNewSetting(newNode);
  }

  @Override
  public void removeAndCondition(@NotNull ArrangementSettingsNode node) {
    doRemoveAndCondition(node);
    refreshConditions();
    notifyListeners();
  }
  
  private void doRemoveAndCondition(@NotNull ArrangementSettingsNode node) {
    if (!(mySettingsNode instanceof ArrangementSettingsCompositeNode)) {
      return;
    }

    ArrangementSettingsNode newNode = mySettingsNode.clone();
    ArrangementSettingsCompositeNode composite = (ArrangementSettingsCompositeNode)newNode;
    composite.getOperands().remove(node);
    if (composite.getOperands().size() == 1) {
      newNode = composite.getOperands().iterator().next();
    }

    applyNewSetting(newNode);
  }

  private void applyNewSetting(@NotNull ArrangementSettingsNode newNode) {
    mySettingsNode = newNode;
    HierarchicalArrangementSettingsNode grouped = myGrouper.group(newNode);
    int newDepth = ArrangementConfigUtil.getDepth(grouped);
    int oldDepth = ArrangementConfigUtil.distance(myTopMost, myBottomMost);
    if (oldDepth == newDepth) {
      myBottomMost.setUserObject(ArrangementConfigUtil.getLast(grouped).getCurrent());
      return;
    }

    Pair<DefaultMutableTreeNode, Integer> replacement = ArrangementConfigUtil.map(null, grouped);
    DefaultMutableTreeNode newBottom = replacement.first;
    DefaultMutableTreeNode newTop = ArrangementConfigUtil.getRoot(newBottom);
    final TIntIntHashMap rowChanges = ArrangementConfigUtil.replace(myTopMost, myBottomMost, newTop);
    myTopMost = newTop;
    myBottomMost = newBottom;

    final TIntObjectHashMap<ArrangementRuleEditingModelImpl> newMappings = new TIntObjectHashMap<ArrangementRuleEditingModelImpl>();

    // Update model mappings.
    myRowMappings.forEachEntry(new TIntObjectProcedure<ArrangementRuleEditingModelImpl>() {
      @Override
      public boolean execute(int row, ArrangementRuleEditingModelImpl model) {
        if (row == myRow) {
          return true;
        }
        if (rowChanges.containsKey(row)) {
          newMappings.put(rowChanges.get(row), model);
        }
        else {
          newMappings.put(row, model);
        }
        model.refreshTreeNodes();
        return true;
      }
    });
    myRow = ArrangementConfigUtil.getRow(myBottomMost);
    newMappings.put(myRow, this);

    myRowMappings.clear();
    newMappings.forEachEntry(new TIntObjectProcedure<ArrangementRuleEditingModelImpl>() {
      @Override
      public boolean execute(int row, ArrangementRuleEditingModelImpl model) {
        myRowMappings.put(row, model);
        return true;
      }
    });
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
  }

  private void notifyListeners() {
    for (Listener listener : myListeners) {
      listener.onChanged(myTopMost, myBottomMost);
    }
  }

  @Override
  public String toString() {
    return "model for " + mySettingsNode;
  }

  private static class MyConditionsBuilder implements ArrangementSettingsNodeVisitor {

    @NotNull Set<Object> conditions;

    @Override
    public void visit(@NotNull ArrangementSettingsAtomNode node) {
      conditions.add(node.getValue()); 
    }

    @Override
    public void visit(@NotNull ArrangementSettingsCompositeNode node) {
      for (ArrangementSettingsNode operand : node.getOperands()) {
        operand.invite(this);
      } 
    }
  }
}
