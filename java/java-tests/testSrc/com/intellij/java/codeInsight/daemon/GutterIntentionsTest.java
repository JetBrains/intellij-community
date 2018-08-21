// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.IntentionsUI;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.List;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

/**
 * @author Dmitry Avdeev
 */
public class GutterIntentionsTest extends LightCodeInsightFixtureTestCase {
  public void testEmptyIntentions() {
    myFixture.configureByText(JavaFileType.INSTANCE, "class Foo {\n" +
                                                     "  <caret>   private String test() {\n" +
                                                     "        return null;\n" +
                                                     "     }" +
                                                     "}");
    myFixture.findAllGutters();
    List<IntentionAction> intentions = myFixture.getAvailableIntentions();
    assertEmpty(intentions);
  }

  public void testOptions() {
    myFixture.configureByText(JavaFileType.INSTANCE, "public class Foo {\n" +
                                                     "  public static void <caret>main(String[] args) {}" +
                                                     "}");
    assertSize(1, myFixture.findGuttersAtCaret());

    ShowIntentionsPass.IntentionsInfo intentions = ShowIntentionsPass.getActionsToShow(getEditor(), getFile());
    assertThat(intentions.guttersToShow.size()).isGreaterThan(1);
  }

  public void testRunLineMarker() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", "public class MainTest extends junit.framework.TestCase {\n" +
                                               "    public void test<caret>Foo() {\n" +
                                               "    }\n" +
                                               "}");
    myFixture.doHighlighting();
    CachedIntentions intentions = IntentionsUI.getInstance(getProject()).getCachedIntentions(getEditor(), getFile());
    assertThat(intentions.getAllActions().get(0).getText()).startsWith("Run ");
  }
}
