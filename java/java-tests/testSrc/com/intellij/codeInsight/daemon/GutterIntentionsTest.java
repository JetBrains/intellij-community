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
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class GutterIntentionsTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testEmptyIntentions() throws Exception {
    myFixture.configureByText(JavaFileType.INSTANCE, "class Foo {\n" +
                                                     "  <caret>   private String test() {\n" +
                                                     "        return null;\n" +
                                                     "     }");
    List<IntentionAction> intentions = myFixture.getAvailableIntentions();
    assertEmpty(intentions);
  }

  @Override
  protected boolean isWriteActionRequired() {
    return false;
  }
}
