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
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings("ConstantConditions")
public class RunLineMarkerTest extends LightCodeInsightFixtureTestCase {

  public void testRunLineMarker() throws Exception {
    myFixture.configureByText("MainTest.java", "public class MainTest {\n" +
                                               "    public static void <caret>foo(String[] args) {\n" +
                                               "    }\n " +
                                               "    public static void main(String[] args) {\n" +
                                               "    }\n" +
                                               "}");
    assertEquals(0, myFixture.findGuttersAtCaret().size());
    assertEquals(2, myFixture.findAllGutters().size());
  }

  public void testTestClassWithMain() throws Exception {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", "public class <caret>MainTest extends junit.framework.TestCase {\n" +
                                               "    public static void main(String[] args) {\n" +
                                               "    }\n" +
                                               "    public void testFoo() {\n" +
                                               "    }\n" +
                                               "}");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    ActionGroup group = mark.getPopupMenuActions();
    assertNotNull(group);
    TestActionEvent event = new TestActionEvent();
    List<AnAction> list = ContainerUtil.findAll(group.getChildren(event), action -> {
      TestActionEvent actionEvent = new TestActionEvent();
      action.update(actionEvent);
      String text = actionEvent.getPresentation().getText();
      return text != null && text.startsWith("Run ") && text.endsWith("'");
    });
    assertEquals(list.toString(), 2, list.size());
    list.get(0).update(event);
    assertEquals("Run 'MainTest.main()'", event.getPresentation().getText());
    list.get(1).update(event);
    assertEquals("Run 'MainTest'", event.getPresentation().getText());
  }
}
