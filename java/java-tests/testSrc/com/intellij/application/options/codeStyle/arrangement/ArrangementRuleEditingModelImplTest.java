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

    ArrangementTreeNode child = myRoot.getFirstChild();
    assertNotNull(child);
    ArrangementSettingsNode expectedSettingsNode = and(atom(PUBLIC), atom(STATIC));
    assertEquals(expectedSettingsNode, child.getBackingSetting());
    assertEquals(expectedSettingsNode, model.getSettingsNode());
  }

  @Test
  public void buildNewSingleLevel() {
    configure(atom(PUBLIC));
    ArrangementRuleEditingModelImpl model = myRowMappings.get(1);
    assertNotNull(model);
    assertEquals(1, myRowMappings.size());
    
    model.addAndCondition(atom(FIELD));

    assertEquals(2, model.getRow());
    assertEquals(and(atom(FIELD), atom(PUBLIC)), model.getSettingsNode());

    ArrangementTreeNode fieldNode = myRoot.getFirstChild();
    assertNotNull(fieldNode);
    assertEquals(atom(FIELD), fieldNode.getBackingSetting());

    ArrangementTreeNode publicNode = fieldNode.getFirstChild();
    assertNotNull(publicNode);
    assertEquals(atom(PUBLIC), publicNode.getBackingSetting());
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

    ArrangementTreeNode fieldNode = myRoot.getFirstChild();
    assertNotNull(fieldNode);
    assertEquals(atom(FIELD), fieldNode.getBackingSetting());

    ArrangementTreeNode modifiersNode = fieldNode.getFirstChild();
    assertNotNull(modifiersNode);
    assertEquals(and(atom(PUBLIC), atom(STATIC)), modifiersNode.getBackingSetting());
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

    ArrangementTreeNode fieldNode = myRoot.getFirstChild();
    assertNotNull(fieldNode);
    assertEquals(atom(FIELD), fieldNode.getBackingSetting());

    ArrangementTreeNode modifiersNode = fieldNode.getFirstChild();
    assertNotNull(modifiersNode);
    assertEquals(atom(STATIC), modifiersNode.getBackingSetting());
  }

  @Test
  public void removeLastRowCondition() {
    configure(and(atom(FIELD), atom(PUBLIC)));
    ArrangementRuleEditingModelImpl model = myRowMappings.get(2);
    assertNotNull(model);
    assertEquals(1, myRowMappings.size());

    model.removeAndCondition(atom(PUBLIC));

    assertEquals(1, model.getRow());
    assertEquals(atom(FIELD), model.getSettingsNode());

    ArrangementTreeNode fieldNode = myRoot.getFirstChild();
    assertNotNull(fieldNode);
    assertEquals(atom(FIELD), fieldNode.getBackingSetting());
    
    assertEquals(0, fieldNode.getChildCount());
  }

  @Test
  public void removeFirstRowConditionFromMultiChildrenParent() {
    configure(and(atom(FIELD), atom(PUBLIC)));
    configure(and(atom(FIELD), atom(STATIC)));
    
    ArrangementRuleEditingModelImpl modelToChange = myRowMappings.get(2);
    assertNotNull(modelToChange);
    
    ArrangementRuleEditingModelImpl siblingModel = myRowMappings.get(3);
    assertNotNull(siblingModel);
    assertEquals(2, myRowMappings.size());
    
    modelToChange.removeAndCondition(atom(PUBLIC));
    
    assertSame(1, modelToChange.getRow());
    assertEquals(atom(FIELD), modelToChange.getSettingsNode());
    
    assertSame(3, siblingModel.getRow());
    assertEquals(and(atom(FIELD), atom(STATIC)), siblingModel.getSettingsNode());

    ArrangementTreeNode atomFieldNode = myRoot.getFirstChild();
    assertNotNull(atomFieldNode);
    assertEquals(atom(FIELD), atomFieldNode.getBackingSetting());

    ArrangementTreeNode layeredFieldNode = atomFieldNode.getNextSibling();
    assertNotNull(atomFieldNode);
    assertEquals(atom(FIELD), atomFieldNode.getBackingSetting());

    ArrangementTreeNode staticNode = layeredFieldNode.getFirstChild();
    assertNotNull(staticNode);
    assertEquals(atom(STATIC), staticNode.getBackingSetting());
  }

  @Test
  public void removeLastRowConditionFromMultiChildrenParent() {
    configure(and(atom(FIELD), atom(PUBLIC)));
    configure(and(atom(FIELD), atom(STATIC)));

    assertEquals(2, myRowMappings.size());
    ArrangementRuleEditingModel siblingModel = myRowMappings.get(2);
    assertNotNull(siblingModel);
    
    ArrangementRuleEditingModel modelToChange = myRowMappings.get(3);
    assertNotNull(modelToChange);

    modelToChange.removeAndCondition(atom(STATIC));

    assertEquals(2, myRowMappings.size());
    assertSame(siblingModel, myRowMappings.get(2));
    assertEquals(and(atom(FIELD), atom(PUBLIC)), siblingModel.getSettingsNode());

    assertSame(modelToChange, myRowMappings.get(3));
    assertEquals(atom(FIELD), modelToChange.getSettingsNode());

    ArrangementTreeNode compositeFieldNode = myRoot.getFirstChild();
    assertNotNull(compositeFieldNode);
    assertEquals(atom(FIELD), compositeFieldNode.getBackingSetting());

    ArrangementTreeNode publicNode = compositeFieldNode.getFirstChild();
    assertNotNull(publicNode);
    assertEquals(atom(PUBLIC), publicNode.getBackingSetting());

    ArrangementTreeNode atomFieldNode = compositeFieldNode.getNextSibling();
    assertNotNull(atomFieldNode);
    assertEquals(atom(FIELD), atomFieldNode.getBackingSetting());
  }
}
