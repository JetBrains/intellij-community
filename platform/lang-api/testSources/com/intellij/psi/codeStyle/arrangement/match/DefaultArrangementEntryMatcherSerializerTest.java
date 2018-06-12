/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.codeStyle.arrangement.DefaultArrangementSettingsSerializer;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import com.intellij.util.containers.ContainerUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*;
import static org.junit.Assert.*;

/**
 * @author Denis Zhdanov
 * @since 07/18/2012
 */
public class DefaultArrangementEntryMatcherSerializerTest {

  private final DefaultArrangementEntryMatcherSerializer mySerializer =
    new DefaultArrangementEntryMatcherSerializer(DefaultArrangementSettingsSerializer.Mixin.NULL);

  @Test
  public void simpleMatchers() {
    doTest(new ArrangementAtomMatchCondition(CLASS));
    doTest(new ArrangementAtomMatchCondition(PRIVATE));
  }

  @Test
  public void compositeMatchers() {
    ArrangementCompositeMatchCondition condition = new ArrangementCompositeMatchCondition();
    condition.addOperand(new ArrangementAtomMatchCondition(METHOD));
    condition.addOperand(new ArrangementAtomMatchCondition(SYNCHRONIZED));
    doTest(condition);

    condition = new ArrangementCompositeMatchCondition();
    condition.addOperand(new ArrangementAtomMatchCondition(FIELD));
    condition.addOperand(new ArrangementAtomMatchCondition(PUBLIC));
    condition.addOperand(new ArrangementAtomMatchCondition(STATIC));
    condition.addOperand(new ArrangementAtomMatchCondition(FINAL));
  }

  @Test
  public void conditionsOrder() {
    // Inspired by IDEA-91826.
    ArrangementCompositeMatchCondition condition = new ArrangementCompositeMatchCondition();
    ArrangementSettingsToken typeToPreserve = FIELD;
    Set<ArrangementSettingsToken> modifiersToPreserve = ContainerUtilRt.newHashSet(PUBLIC, STATIC, FINAL);
    condition.addOperand(new ArrangementAtomMatchCondition(typeToPreserve, typeToPreserve));
    for (ArrangementSettingsToken modifier : modifiersToPreserve) {
      condition.addOperand(new ArrangementAtomMatchCondition(modifier, modifier));
    }
    Element element = mySerializer.serialize(new StdArrangementEntryMatcher(condition));
    assertNotNull(element);

    // Change hash-container data distribution at the composite condition.
    for (ArrangementSettingsToken type : StdArrangementTokens.EntryType.values()) {
      if (type != typeToPreserve) {
        condition.addOperand(new ArrangementAtomMatchCondition(type, type));
      }
    }
    for (ArrangementSettingsToken modifier : StdArrangementTokens.Modifier.values()) {
      if (!modifiersToPreserve.contains(modifier)) {
        condition.addOperand(new ArrangementAtomMatchCondition(modifier, modifier));
      }
    }
    
    // Revert state to the initial one.
    for (ArrangementSettingsToken type : StdArrangementTokens.EntryType.values()) {
      if (type != typeToPreserve) {
        condition.removeOperand(new ArrangementAtomMatchCondition(type, type));
      }
    }
    for (ArrangementSettingsToken modifier : StdArrangementTokens.Modifier.values()) {
      if (!modifiersToPreserve.contains(modifier)) {
        condition.removeOperand(new ArrangementAtomMatchCondition(modifier, modifier));
      }
    }
    
    // Check that the order is the same
    Element actual = mySerializer.serialize(new StdArrangementEntryMatcher(condition));
    assertNotNull(actual);
    checkElements(element, actual);
  }

  @Test
  public void nameConditionOnly() {
    ArrangementAtomMatchCondition condition = new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.NAME, "get*");
    doTest(condition);
  }
  
  @Test
  public void compositeConditionWithName() {
    ArrangementCompositeMatchCondition condition = new ArrangementCompositeMatchCondition();
    condition.addOperand(new ArrangementAtomMatchCondition(METHOD));
    condition.addOperand(new ArrangementAtomMatchCondition(SYNCHRONIZED));
    condition.addOperand(new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.NAME, ("get*")));
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
    if (children1.isEmpty()) {
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
    StdArrangementEntryMatcher matcher = mySerializer.deserialize(element);
    assertNotNull(matcher);
    assertEquals(condition, matcher.getCondition());
  }
}
