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

import com.intellij.psi.codeStyle.arrangement.ModifierAwareArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.TypeAwareArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class StandardArrangementEntryMatcherTest {

  private Mockery myMockery;
  
  @Before
  public void setUp() {
    myMockery = new JUnit4Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
  }
  
  @After
  public void checkExpectations() {
    myMockery.assertIsSatisfied();
  }

  @Test
  public void atomCondition() {
    ArrangementAtomMatchCondition condition = new ArrangementAtomMatchCondition(FIELD);
    
    StdArrangementEntryMatcher matcher = new StdArrangementEntryMatcher(condition);
    assertEquals(condition, matcher.getCondition());

    final TypeAwareArrangementEntry fieldEntry = myMockery.mock(TypeAwareArrangementEntry.class, "field");
    final TypeAwareArrangementEntry classEntry = myMockery.mock(TypeAwareArrangementEntry.class, "class");
    final ModifierAwareArrangementEntry publicEntry = myMockery.mock(ModifierAwareArrangementEntry.class, "public");
    myMockery.checking(new Expectations() {{
      allowing(fieldEntry).getTypes(); will(returnValue(Set.of(FIELD)));
      allowing(classEntry).getTypes(); will(returnValue(Set.of(CLASS)));
      allowing(publicEntry).getModifiers(); will(returnValue(Set.of(PUBLIC)));
    }});
    
    assertTrue(matcher.isMatched(fieldEntry));
    assertFalse(matcher.isMatched(classEntry));
    assertFalse(matcher.isMatched(publicEntry));
  }

  @Test
  public void compositeAndCondition() {
    ArrangementCompositeMatchCondition condition = new ArrangementCompositeMatchCondition();
    condition.addOperand(new ArrangementAtomMatchCondition(FIELD));
    condition.addOperand(new ArrangementAtomMatchCondition(PUBLIC));

    StdArrangementEntryMatcher matcher = new StdArrangementEntryMatcher(condition);
    assertEquals(condition, matcher.getCondition());

    final TypeAwareArrangementEntry fieldEntry = myMockery.mock(TypeAwareArrangementEntry.class, "field");
    final ModifierAwareArrangementEntry publicEntry = myMockery.mock(ModifierAwareArrangementEntry.class, "public");
    final TypeAndModifierAware privateFieldEntry = myMockery.mock(TypeAndModifierAware.class, "private field");
    final TypeAndModifierAware publicMethodEntry = myMockery.mock(TypeAndModifierAware.class, "public method");
    final TypeAndModifierAware publicFieldEntry = myMockery.mock(TypeAndModifierAware.class, "public field");
    final TypeAndModifierAware publicStaticFieldEntry = myMockery.mock(TypeAndModifierAware.class, "public static field");
    myMockery.checking(new Expectations() {{
      allowing(fieldEntry).getTypes(); will(returnValue(Set.of(FIELD)));
      
      allowing(publicEntry).getModifiers(); will(returnValue(Set.of(PUBLIC)));
      
      allowing(privateFieldEntry).getTypes(); will(returnValue(Set.of(FIELD)));
      allowing(privateFieldEntry).getModifiers(); will(returnValue(Set.of(PRIVATE)));
      
      allowing(publicMethodEntry).getTypes(); will(returnValue(Set.of(METHOD)));
      allowing(publicMethodEntry).getModifiers(); will(returnValue(Set.of(PUBLIC)));
      
      allowing(publicFieldEntry).getTypes(); will(returnValue(Set.of(FIELD)));
      allowing(publicFieldEntry).getModifiers(); will(returnValue(Set.of(PUBLIC)));

      allowing(publicStaticFieldEntry).getTypes(); will(returnValue(Set.of(FIELD)));
      allowing(publicStaticFieldEntry).getModifiers(); will(returnValue(Set.of(PUBLIC, STATIC)));
    }});
    
    assertFalse(matcher.isMatched(fieldEntry));
    assertFalse(matcher.isMatched(publicEntry));
    assertFalse(matcher.isMatched(privateFieldEntry));
    assertFalse(matcher.isMatched(publicMethodEntry));
    assertTrue(matcher.isMatched(publicFieldEntry));
    assertTrue(matcher.isMatched(publicStaticFieldEntry));
  }
}
