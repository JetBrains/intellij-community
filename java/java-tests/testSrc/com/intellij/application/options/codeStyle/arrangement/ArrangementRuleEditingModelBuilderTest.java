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

import com.intellij.psi.codeStyle.arrangement.JavaRearranger;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsAtomNode;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import com.intellij.ui.treeStructure.Tree;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import java.util.Arrays;

import static com.intellij.psi.codeStyle.arrangement.ArrangementUtil.and;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType.FIELD;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.*;
import static org.junit.Assert.*;

/**
 * @author Denis Zhdanov
 * @since 08/15/2012
 */
public class ArrangementRuleEditingModelBuilderTest {

  @NotNull private ArrangementRuleEditingModelBuilder             myBuilder;
  @NotNull private JTree                                          myTree;
  @NotNull private DefaultMutableTreeNode                         myRoot;
  @NotNull private TIntObjectHashMap<ArrangementRuleEditingModel> myRowMappings;
  @NotNull private JavaRearranger                                 myGrouper;

  @Before
  public void setUp() {
    myBuilder = new ArrangementRuleEditingModelBuilder();
    myRoot = new DefaultMutableTreeNode();
    myTree = new Tree(myRoot);
    myTree.expandPath(new TreePath(myRoot));
    myRowMappings = new TIntObjectHashMap<ArrangementRuleEditingModel>();
    myGrouper = new JavaRearranger();
  }

  @Test
  public void mapToTheSameLayer() {
    ArrangementSettingsNode settingsNode = and(atom(PUBLIC), atom(STATIC));
    myBuilder.build(settingsNode, myTree, myRoot, myGrouper, myRowMappings);
    checkRows(1);
    ArrangementRuleEditingModel model = myRowMappings.get(1);
    assertTrue(model.hasCondition(PUBLIC));
    assertTrue(model.hasCondition(STATIC));
    assertFalse(model.hasCondition(PRIVATE));
    assertEquals(1, myRoot.getChildCount());
    assertEquals(settingsNode, ((DefaultMutableTreeNode)myRoot.getFirstChild()).getUserObject());
  }

  @Test
  public void splitIntoTwoLayers() {
    ArrangementSettingsNode settingsNode = and(atom(FIELD), atom(PUBLIC), atom(STATIC));
    myBuilder.build(settingsNode, myTree, myRoot, myGrouper, myRowMappings);
    
    checkRows(2);
    
    DefaultMutableTreeNode fieldUiNode = (DefaultMutableTreeNode)myRoot.getFirstChild();
    assertNotNull(fieldUiNode);
    assertEquals(atom(FIELD), fieldUiNode.getUserObject());

    DefaultMutableTreeNode modifiersUiNode = (DefaultMutableTreeNode)fieldUiNode.getFirstChild();
    assertNotNull(modifiersUiNode);
    assertEquals(and(atom(PUBLIC), atom(STATIC)), modifiersUiNode.getUserObject());
  }

  @Test
  public void addToExistingLayer() {
    myBuilder.build(and(atom(PUBLIC), atom(STATIC), atom(FIELD)), myTree, myRoot, myGrouper, myRowMappings);
    myBuilder.build(and(atom(PRIVATE), atom(FIELD)), myTree, myRoot, myGrouper, myRowMappings);
    
    checkRows(2, 3);

    DefaultMutableTreeNode fieldUiNode = (DefaultMutableTreeNode)myRoot.getFirstChild();
    assertNotNull(fieldUiNode);
    assertEquals(atom(FIELD), fieldUiNode.getUserObject());

    DefaultMutableTreeNode publicStaticUiNode = (DefaultMutableTreeNode)fieldUiNode.getFirstChild();
    assertNotNull(publicStaticUiNode);
    assertEquals(and(atom(PUBLIC), atom(STATIC)), publicStaticUiNode.getUserObject());

    DefaultMutableTreeNode privateUiNode = (DefaultMutableTreeNode)fieldUiNode.getLastChild();
    assertNotNull(privateUiNode);
    assertEquals(atom(PRIVATE), privateUiNode.getUserObject());
  }
  
  private void checkRows(int ... rows) {
    for (int row : rows) {
      assertTrue(
        String.format("Expected to find mappings for rows %s. Actual: %s", Arrays.toString(rows), Arrays.toString(myRowMappings.keys())),
        myRowMappings.containsKey(row)
      );
    }
  }
  
  private static ArrangementSettingsAtomNode atom(@NotNull Object condition) {
    final ArrangementSettingType type;
    if (condition instanceof ArrangementEntryType) {
      type = ArrangementSettingType.TYPE;
    }
    else if (condition instanceof ArrangementModifier) {
      type = ArrangementSettingType.MODIFIER;
    }
    else {
      throw new IllegalArgumentException(String.format("Unexpected condition of class %s: %s", condition.getClass(), condition));
    }
    return new ArrangementSettingsAtomNode(type, condition);
  }
}
