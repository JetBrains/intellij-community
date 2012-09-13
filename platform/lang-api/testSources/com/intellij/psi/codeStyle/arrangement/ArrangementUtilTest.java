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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.*;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Denis Zhdanov
 * @since 9/12/12 8:10 PM
 */
public class ArrangementUtilTest {

  @Test
  public void oneLevelGroupingAndMultipleNonGroupedNodes() {
    ArrangementCompositeMatchCondition condition = new ArrangementCompositeMatchCondition(ArrangementOperator.AND);
    ArrangementMatchCondition abstractCondition =
      new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.ABSTRACT);
    ArrangementMatchCondition methodCondition
      = new ArrangementAtomMatchCondition(ArrangementSettingType.TYPE, ArrangementEntryType.METHOD);
    ArrangementMatchCondition publicCondition =
      new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.PUBLIC);
    condition.addOperand(abstractCondition);
    condition.addOperand(methodCondition);
    condition.addOperand(publicCondition);

    HierarchicalArrangementConditionNode grouped =
      ArrangementUtil.group(condition, Collections.singletonList(Collections.singleton(methodCondition)));
    assertEquals(methodCondition, grouped.getCurrent());
    
    HierarchicalArrangementConditionNode child = grouped.getChild();
    assertNotNull(child);
    ArrangementCompositeMatchCondition expectedChildCondition = new ArrangementCompositeMatchCondition(ArrangementOperator.AND);
    expectedChildCondition.addOperand(abstractCondition);
    expectedChildCondition.addOperand(publicCondition);
    assertEquals(expectedChildCondition, child.getCurrent());
    
    assertNull(child.getChild());
  }

  @Test
  public void oneLevelGroupingAndSingleNonGroupedNode() {
    ArrangementCompositeMatchCondition condition = new ArrangementCompositeMatchCondition(ArrangementOperator.AND);
    ArrangementMatchCondition abstractCondition =
      new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.ABSTRACT);
    ArrangementMatchCondition methodCondition
      = new ArrangementAtomMatchCondition(ArrangementSettingType.TYPE, ArrangementEntryType.METHOD);
    condition.addOperand(abstractCondition);
    condition.addOperand(methodCondition);

    HierarchicalArrangementConditionNode grouped =
      ArrangementUtil.group(condition, Collections.singletonList(Collections.singleton(methodCondition)));
    assertEquals(methodCondition, grouped.getCurrent());

    HierarchicalArrangementConditionNode child = grouped.getChild();
    assertNotNull(child);
    assertEquals(abstractCondition, child.getCurrent());

    assertNull(child.getChild());
  }

  @Test
  public void groupSingleNode() {
    ArrangementMatchCondition condition = new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.ABSTRACT);
    HierarchicalArrangementConditionNode grouped =
      ArrangementUtil.group(condition, Collections.singletonList(Collections.singleton(condition)));
    assertEquals(condition, grouped.getCurrent());
    assertNull(grouped.getChild());
  }
}
