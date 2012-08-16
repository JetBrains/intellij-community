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

import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import org.junit.Test;

import javax.swing.tree.DefaultMutableTreeNode;

import static com.intellij.psi.codeStyle.arrangement.ArrangementUtil.and;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType.FIELD;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.*;
import static org.junit.Assert.*;

/**
 * @author Denis Zhdanov
 * @since 08/15/2012
 */
public class ArrangementRuleEditingModelBuilderTest extends AbstractArrangementRuleEditingModelTest {

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
}
