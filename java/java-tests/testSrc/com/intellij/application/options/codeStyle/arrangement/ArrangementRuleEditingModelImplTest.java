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
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.PUBLIC;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.STATIC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * @author Denis Zhdanov
 * @since 8/16/12 10:07 AM
 */
public class ArrangementRuleEditingModelImplTest extends AbstractArrangementRuleEditingModelTest {

  @Test
  public void addConditionToSameLevel() {
    configure(atom(PUBLIC));
    ArrangementRuleEditingModel model = myRowMappings.get(1);
    assertNotNull(model);
    model.addAndCondition(atom(STATIC));

    DefaultMutableTreeNode child = (DefaultMutableTreeNode)myRoot.getFirstChild();
    assertNotNull(child);
    ArrangementSettingsNode expectedSettingsNode = and(atom(PUBLIC), atom(STATIC));
    assertEquals(expectedSettingsNode, child.getUserObject());
    assertEquals(expectedSettingsNode, model.getSettingsNode());
  }

  @Test
  public void buildNewSingleLevel() {
    configure(atom(PUBLIC));
    ArrangementRuleEditingModel model = myRowMappings.get(1);
    assertNotNull(model);
    assertEquals(1, myRowMappings.size());
    
    model.addAndCondition(atom(FIELD));

    assertEquals(1, myRowMappings.size());
    assertSame(model, myRowMappings.get(2));
    assertEquals(and(atom(FIELD), atom(PUBLIC)), model.getSettingsNode());

    DefaultMutableTreeNode fieldNode = (DefaultMutableTreeNode)myRoot.getFirstChild();
    assertNotNull(fieldNode);
    assertEquals(atom(FIELD), fieldNode.getUserObject());

    DefaultMutableTreeNode publicNode = fieldNode.getFirstLeaf();
    assertNotNull(publicNode);
    assertEquals(atom(PUBLIC), publicNode.getUserObject());
  }

  @Test
  public void addConditionToSameNestedLevel() {
    configure(and(atom(FIELD), atom(PUBLIC)));
    ArrangementRuleEditingModel model = myRowMappings.get(2);
    assertNotNull(model);
    assertEquals(1, myRowMappings.size());
    
    model.addAndCondition(atom(STATIC));

    assertEquals(1, myRowMappings.size());
    assertSame(model, myRowMappings.get(2));
    assertEquals(and(atom(FIELD), atom(PUBLIC), atom(STATIC)), model.getSettingsNode());

    DefaultMutableTreeNode fieldNode = (DefaultMutableTreeNode)myRoot.getFirstChild();
    assertNotNull(fieldNode);
    assertEquals(atom(FIELD), fieldNode.getUserObject());

    DefaultMutableTreeNode modifiersNode = fieldNode.getFirstLeaf();
    assertNotNull(modifiersNode);
    assertEquals(and(atom(PUBLIC), atom(STATIC)), modifiersNode.getUserObject());
  }
}
