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
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Arrays;
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

  @NotNull private final TIntObjectHashMap<ArrangementRuleEditingModel> myRowMappings;
  @NotNull private final ArrangementSettingsGrouper                     myGrouper;

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
                                         @NotNull TIntObjectHashMap<ArrangementRuleEditingModel> mappings,
                                         int row)
  {
    mySettingsNode = node;
    myTopMost = topMost;
    myBottomMost = bottomMost;
    myGrouper = grouper;
    myRowMappings = mappings;
    myRow = row;
    extractConditions(node);
  }

  private void extractConditions(@NotNull ArrangementSettingsNode node) {
    myConditions.clear();
    CONDITIONS_BUILDER.conditions = myConditions;
    try {
      node.invite(CONDITIONS_BUILDER);
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

  @Override
  public boolean hasCondition(@NotNull Object key) {
    return myConditions.contains(key);
  }

  @Override
  public void addAndCondition(@NotNull ArrangementSettingsAtomNode node) {
    doAddAndCondition(node);
    extractConditions(mySettingsNode);
    notifyListeners();
  }
  
  private void doAddAndCondition(@NotNull ArrangementSettingsAtomNode node) {
    ArrangementSettingsNode newNode = ArrangementUtil.and(mySettingsNode.clone(), node);
    HierarchicalArrangementSettingsNode grouped = myGrouper.group(newNode);
    int newDepth = ArrangementConfigUtil.getDepth(grouped);
    int oldDepth = ArrangementConfigUtil.distance(myTopMost, myBottomMost);
    if (newDepth == oldDepth) {
      mySettingsNode = newNode;
      myBottomMost.setUserObject(ArrangementConfigUtil.getLast(grouped).getCurrent());
      return;
    }
    
    mySettingsNode = newNode;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode)myTopMost.getParent();
    parent.remove(myTopMost);
    Pair<DefaultMutableTreeNode,Integer> pair = ArrangementConfigUtil.map(parent, grouped);
    myTopMost = (DefaultMutableTreeNode)ArrangementConfigUtil.getLastBefore(pair.first, parent);
    myBottomMost = pair.first;
    int depthShift = newDepth - oldDepth;
    int[] rows = myRowMappings.keys();
    Arrays.sort(rows);
    for (int i = rows.length - 1; i >= 0; i--) {
      int row = rows[i];
      if (row >= myRow) {
        myRowMappings.put(row + depthShift, myRowMappings.get(row));
        myRowMappings.remove(row);
      }
      else {
        break;
      }
    }
    myRow += depthShift;
  }
  
  @Override
  public void removeAndCondition(@NotNull ArrangementSettingsNode node) {
    // TODO den implement 
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
