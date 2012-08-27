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
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.ArrangementOperator;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Denis Zhdanov
 * @since 07/18/2012
 */
public class DefaultArrangementEntryMatcherSerializerTest {

  private final DefaultArrangementEntryMatcherSerializer mySerializer = new DefaultArrangementEntryMatcherSerializer();

  @Test
  public void simpleMatchers() {
    doTest(new ArrangementAtomMatchCondition(ArrangementSettingType.TYPE, ArrangementEntryType.CLASS));
    doTest(new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.PRIVATE));
  }

  @Test
  public void compositeMatchers() {
    ArrangementCompositeMatchCondition condition = new ArrangementCompositeMatchCondition(ArrangementOperator.AND);
    condition.addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.TYPE, ArrangementEntryType.METHOD));
    condition.addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.SYNCHRONIZED));
    doTest(condition);
    
    condition = new ArrangementCompositeMatchCondition(ArrangementOperator.AND);
    condition.addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.TYPE, ArrangementEntryType.FIELD));
    condition.addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.PUBLIC));
    condition.addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.STATIC));
    condition.addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.FINAL));
  }

  private void doTest(@NotNull ArrangementMatchCondition condition) {
    Element element = mySerializer.serialize(new StdArrangementEntryMatcher(condition));
    assertNotNull(String.format("Can't serialize match condition '%s'", condition), element);
    ArrangementEntryMatcher matcher = mySerializer.deserialize(element);
    assertNotNull(matcher);
    assertTrue(matcher instanceof StdArrangementEntryMatcher);
    assertEquals(condition, ((StdArrangementEntryMatcher)matcher).getCondition());
  }
}
