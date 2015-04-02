/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.fileTemplates;

import com.intellij.ide.fileTemplates.actions.CreateFromTemplateAction;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Condition;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.TestDataProvider;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

public class JavaFileTemplatesTest extends LightCodeInsightFixtureTestCase {

  public void testCreateFromTemplateGroup() throws Exception {

    myFixture.configureByText("foo.java", "");
    AnAction[] children = new CreateFromTemplateGroup().getChildren(new TestActionEvent(new TestDataProvider(getProject())));
    assertNull(ContainerUtil.find(children, new Condition<AnAction>() {
      @Override
      public boolean value(AnAction action) {
        return action instanceof CreateFromTemplateAction && ((CreateFromTemplateAction)action).getTemplate().getName().equals("Class");
      }
    }));
    assertNotNull(ContainerUtil.find(children, new Condition<AnAction>() {
      @Override
      public boolean value(AnAction action) {
        return action instanceof CreateFromTemplateAction && ((CreateFromTemplateAction)action).getTemplate().getName().equals("Singleton");
      }
    }));
  }
}
