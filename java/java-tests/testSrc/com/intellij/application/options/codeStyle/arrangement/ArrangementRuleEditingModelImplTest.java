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

import com.intellij.openapi.util.Ref;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import gnu.trove.TObjectProcedure;
import org.junit.Test;

import javax.swing.tree.DefaultMutableTreeNode;

import static com.intellij.psi.codeStyle.arrangement.ArrangementUtil.and;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType.FIELD;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.PUBLIC;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.STATIC;
import static org.junit.Assert.*;

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

  @Test
  public void removeAndKeepAllLevels() {
    configure(and(atom(FIELD), atom(PUBLIC), atom(STATIC)));
    ArrangementRuleEditingModel model = myRowMappings.get(2);
    assertNotNull(model);
    assertEquals(1, myRowMappings.size());
    
    model.removeAndCondition(atom(PUBLIC));

    assertEquals(1, myRowMappings.size());
    assertSame(model, myRowMappings.get(2));
    assertEquals(and(atom(FIELD), atom(STATIC)), model.getSettingsNode());

    DefaultMutableTreeNode fieldNode = (DefaultMutableTreeNode)myRoot.getFirstChild();
    assertNotNull(fieldNode);
    assertEquals(atom(FIELD), fieldNode.getUserObject());

    DefaultMutableTreeNode modifiersNode = fieldNode.getFirstLeaf();
    assertNotNull(modifiersNode);
    assertEquals(atom(STATIC), modifiersNode.getUserObject());
  }

  @Test
  public void removeLastRowCondition() {
    configure(and(atom(FIELD), atom(PUBLIC)));
    ArrangementRuleEditingModel model = myRowMappings.get(2);
    assertNotNull(model);
    assertEquals(1, myRowMappings.size());

    model.removeAndCondition(atom(PUBLIC));

    assertEquals(1, myRowMappings.size());
    assertSame(model, myRowMappings.get(1));
    assertEquals(atom(FIELD), model.getSettingsNode());

    DefaultMutableTreeNode fieldNode = (DefaultMutableTreeNode)myRoot.getFirstChild();
    assertNotNull(fieldNode);
    assertEquals(atom(FIELD), fieldNode.getUserObject());
    
    assertEquals(0, fieldNode.getChildCount());
  }

  @Test
  public void removeLastRowConditionFromMultiChildrenParent() {
    configure(and(atom(FIELD), atom(PUBLIC)));
    configure(and(atom(FIELD), atom(STATIC)));
    
    ArrangementRuleEditingModel modelToChange = myRowMappings.get(2);
    assertNotNull(modelToChange);
    
    ArrangementRuleEditingModel siblingModel = myRowMappings.get(3);
    assertNotNull(siblingModel);
    assertEquals(2, myRowMappings.size());
    
    modelToChange.removeAndCondition(atom(PUBLIC));
    
    assertEquals(2, myRowMappings.size());
    assertSame(modelToChange, myRowMappings.get(1));
    assertEquals(atom(FIELD), modelToChange.getSettingsNode());
    
    assertSame(siblingModel, myRowMappings.get(3));
    assertEquals(and(atom(FIELD), atom(STATIC)), siblingModel.getSettingsNode());
    
    DefaultMutableTreeNode atomFieldNode = (DefaultMutableTreeNode)myRoot.getFirstChild();
    assertNotNull(atomFieldNode);
    assertEquals(atom(FIELD), atomFieldNode.getUserObject());
    
    DefaultMutableTreeNode layeredFieldNode = atomFieldNode.getNextNode();
    assertNotNull(atomFieldNode);
    assertEquals(atom(FIELD), atomFieldNode.getUserObject());
    
    DefaultMutableTreeNode staticNode = (DefaultMutableTreeNode)layeredFieldNode.getFirstChild();
    assertNotNull(staticNode);
    assertEquals(atom(STATIC), staticNode.getUserObject());
    
    //checkTreeNodesConsistency();
  }
  
  private void checkTreeNodesConsistency() {
    final Ref<DefaultMutableTreeNode> rootRef = new Ref<DefaultMutableTreeNode>();
    myRowMappings.forEachValue(new TObjectProcedure<ArrangementRuleEditingModelImpl>() {
      @Override
      public boolean execute(ArrangementRuleEditingModelImpl model) {
        DefaultMutableTreeNode root = ArrangementConfigUtil.getRoot(model.getTopMost());
        assertSame(root, ArrangementConfigUtil.getRoot(model.getBottomMost()));

        if (rootRef.get() == null) {
          rootRef.set(root);
        }
        else {
          assertSame(rootRef.get(), root);
        }
        return true;
      }
    });
  }
}
