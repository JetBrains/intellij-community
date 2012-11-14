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

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType.CLASS;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType.FIELD;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType.METHOD;
import static com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier.*;
import static com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType.MODIFIER;
import static com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType.NAME;
import static com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType.TYPE;
import static org.junit.Assert.*;

/**
 * @author Denis Zhdanov
 * @since 07/18/2012
 */
public class DefaultArrangementEntryMatcherSerializerTest {

  private final DefaultArrangementEntryMatcherSerializer mySerializer = new DefaultArrangementEntryMatcherSerializer();

  @Test
  public void simpleMatchers() {
    doTest(new ArrangementAtomMatchCondition(TYPE, CLASS));
    doTest(new ArrangementAtomMatchCondition(MODIFIER, PRIVATE));
  }

  @Test
  public void compositeMatchers() {
    ArrangementCompositeMatchCondition condition = new ArrangementCompositeMatchCondition();
    condition.addOperand(new ArrangementAtomMatchCondition(TYPE, METHOD));
    condition.addOperand(new ArrangementAtomMatchCondition(MODIFIER, SYNCHRONIZED));
    doTest(condition);
    
    condition = new ArrangementCompositeMatchCondition();
    condition.addOperand(new ArrangementAtomMatchCondition(TYPE, FIELD));
    condition.addOperand(new ArrangementAtomMatchCondition(MODIFIER, PUBLIC));
    condition.addOperand(new ArrangementAtomMatchCondition(MODIFIER, STATIC));
    condition.addOperand(new ArrangementAtomMatchCondition(MODIFIER, FINAL));
  }

  @Test
  public void conditionsOrder() {
    // Inspired by IDEA-91826.
    ArrangementCompositeMatchCondition condition = new ArrangementCompositeMatchCondition();
    ArrangementEntryType typeToPreserve = FIELD;
    Set<ArrangementModifier> modifiersToPreserve = EnumSet.of(PUBLIC, STATIC, FINAL);
    condition.addOperand(new ArrangementAtomMatchCondition(TYPE, typeToPreserve));
    for (ArrangementModifier modifier : modifiersToPreserve) {
      condition.addOperand(new ArrangementAtomMatchCondition(MODIFIER, modifier));
    }
    Element element = mySerializer.serialize(new StdArrangementEntryMatcher(condition));
    assertNotNull(element);
    
    // Change hash-container data distribution at the composite condition.
    for (ArrangementEntryType type : ArrangementEntryType.values()) {
      if (type != typeToPreserve) {
        condition.addOperand(new ArrangementAtomMatchCondition(TYPE, type));
      }
    }
    for (ArrangementModifier modifier : values()) {
      if (!modifiersToPreserve.contains(modifier)) {
        condition.addOperand(new ArrangementAtomMatchCondition(MODIFIER, modifier));
      }
    }
    
    // Revert state to the initial one.
    for (ArrangementEntryType type : ArrangementEntryType.values()) {
      if (type != typeToPreserve) {
        condition.removeOperand(new ArrangementAtomMatchCondition(TYPE, type));
      }
    }
    for (ArrangementModifier modifier : values()) {
      if (!modifiersToPreserve.contains(modifier)) {
        condition.removeOperand(new ArrangementAtomMatchCondition(MODIFIER, modifier));
      }
    }
    
    // Check that the order is the same
    Element actual = mySerializer.serialize(new StdArrangementEntryMatcher(condition));
    assertNotNull(actual);
    checkElements(element, actual);
  }

  @Test
  public void nameConditionOnly() {
    ArrangementAtomMatchCondition condition = new ArrangementAtomMatchCondition(NAME, "get*");
    doTest(condition);
  }
  
  @Test
  public void compositeConditionWithName() {
    ArrangementCompositeMatchCondition condition = new ArrangementCompositeMatchCondition();
    condition.addOperand(new ArrangementAtomMatchCondition(TYPE, METHOD));
    condition.addOperand(new ArrangementAtomMatchCondition(MODIFIER, SYNCHRONIZED));
    condition.addOperand(new ArrangementAtomMatchCondition(NAME, ("get*")));
    doTest(condition);
  }

  private static void checkElements(@NotNull Element expected, @NotNull Element actual) {
    assertTrue(
      String.format("Tag name mismatch - expected: '%s', actual: '%s'", expected.getName(), actual.getName()),
      Comparing.equal(expected.getName(), actual.getName())
    );
    List children1 = expected.getChildren();
    List children2 = actual.getChildren();
    assertEquals(children1.size(), children2.size());
    if (children1.size() == 0) {
      assertTrue(
        String.format("Content mismatch - expected: '%s', actual: '%s'", expected.getText(), actual.getText()),
        Comparing.equal(expected.getText(), actual.getText())
      );
    }
    else {
      for (int i = 0; i < children1.size(); i++) {
        checkElements((Element)children1.get(i), (Element)children2.get(i));
      }
    }
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
