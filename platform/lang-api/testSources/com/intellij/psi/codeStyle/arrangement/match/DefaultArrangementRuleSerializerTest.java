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

import com.intellij.psi.codeStyle.arrangement.ArrangementRule;
import com.intellij.psi.codeStyle.arrangement.ArrangementRuleSerializer;
import com.intellij.psi.codeStyle.arrangement.ArrangementRuleUtil;
import com.intellij.psi.codeStyle.arrangement.DefaultArrangementRuleSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Denis Zhdanov
 * @since 07/18/2012
 */
public class DefaultArrangementRuleSerializerTest {

  private ArrangementRuleSerializer mySerializer;

  @Before
  public void setUp() {
    mySerializer = DefaultArrangementRuleSerializer.INSTANCE;
  }

  @Test
  public void simpleMatchers() {
    doTest(new ByTypeArrangementEntryMatcher(ArrangementEntryType.CLASS));
    doTest(new ByTypeArrangementEntryMatcher(ArrangementEntryType.values()));
  }
  
  @Test
  public void compositeMatchers() {
    doTest(ArrangementRuleUtil.or(
      new ByTypeArrangementEntryMatcher(ArrangementEntryType.FIELD),
      new ByTypeArrangementEntryMatcher(ArrangementEntryType.METHOD))
    );
    
    doTest(ArrangementRuleUtil.and(
      ArrangementRuleUtil.or(
        new ByTypeArrangementEntryMatcher(ArrangementEntryType.METHOD),
        new ByNameArrangementEntryMatcher("get*")
      )
    ));
  }
  
  private void doTest(@NotNull ArrangementEntryMatcher matcher) {
    doTest(new ArrangementRule(matcher));
  }
  
  private void doTest(@NotNull ArrangementRule rule) {
    Element element = mySerializer.serialize(rule);
    assertNotNull(String.format("Can't serialize rule '%s'", rule), element);
    assertEquals(rule, mySerializer.deserialize(element));
  }
}
