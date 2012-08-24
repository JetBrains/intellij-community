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

import org.junit.Test;

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
    configure(and(atom(PUBLIC), atom(STATIC)));
    checkRows(1);
    ArrangementRuleEditingModel model = myRowMappings.get(1);
    assertTrue(model.hasCondition(PUBLIC));
    assertTrue(model.hasCondition(STATIC));
    assertFalse(model.hasCondition(PRIVATE));
    assertEquals(1, myRoot.getChildCount());
    assertEquals(and(atom(PUBLIC), atom(STATIC)), myRoot.getFirstChild().getBackingCondition());
  }

  @Test
  public void splitIntoTwoLayers() {
    configure(and(atom(FIELD), atom(PUBLIC), atom(STATIC)));
    
    checkRows(2);

    ArrangementTreeNode fieldUiNode = myRoot.getFirstChild();
    assertNotNull(fieldUiNode);
    assertEquals(atom(FIELD), fieldUiNode.getBackingCondition());

    ArrangementTreeNode modifiersUiNode = fieldUiNode.getFirstChild();
    assertNotNull(modifiersUiNode);
    assertEquals(and(atom(PUBLIC), atom(STATIC)), modifiersUiNode.getBackingCondition());
  }

  @Test
  public void addToExistingLayer() {
    configure(and(atom(PUBLIC), atom(STATIC), atom(FIELD)));
    configure(and(atom(PRIVATE), atom(FIELD)));
    
    checkRows(2, 3);

    ArrangementTreeNode fieldUiNode = myRoot.getFirstChild();
    assertNotNull(fieldUiNode);
    assertEquals(atom(FIELD), fieldUiNode.getBackingCondition());

    ArrangementTreeNode publicStaticUiNode = fieldUiNode.getFirstChild();
    assertNotNull(publicStaticUiNode);
    assertEquals(and(atom(PUBLIC), atom(STATIC)), publicStaticUiNode.getBackingCondition());

    ArrangementTreeNode privateUiNode = fieldUiNode.getLastChild();
    assertNotNull(privateUiNode);
    assertEquals(atom(PRIVATE), privateUiNode.getBackingCondition());
  }
}
