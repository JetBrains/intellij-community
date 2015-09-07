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

import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class GutterIntentionsTest extends LightCodeInsightFixtureTestCase {
  public void testEmptyIntentions() throws Exception {
    myFixture.configureByText(JavaFileType.INSTANCE, "class Foo {\n" +
                                                     "  <caret>   private String test() {\n" +
                                                     "        return null;\n" +
                                                     "     }" +
                                                     "}");
    myFixture.findAllGutters();
    List<IntentionAction> intentions = myFixture.getAvailableIntentions();
    assertEmpty(intentions);
  }

  public void testOptions() throws Exception {
    myFixture.configureByText(JavaFileType.INSTANCE, "public class Foo {\n" +
                                                     "  public static void <caret>main(String[] args) {}" +
                                                     "}");
    assertEquals(1, myFixture.findGuttersAtCaret().size());

    ShowIntentionsPass.IntentionsInfo intentions = new ShowIntentionsPass.IntentionsInfo();
    ShowIntentionsPass.getActionsToShow(getEditor(), getFile(), intentions, -1);
    assertEquals(1, intentions.guttersToShow.size());
    List<IntentionAction> options = intentions.guttersToShow.get(0).getOptions(myFixture.getElementAtCaret(), getEditor());
    assertNotNull(options);
    assertNotEmpty(options);
  }
}
