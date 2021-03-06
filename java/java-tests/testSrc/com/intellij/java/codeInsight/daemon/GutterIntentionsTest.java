// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.IntentionsUI;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclarationLocalInspection;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

/**
 * @author Dmitry Avdeev
 */
public class GutterIntentionsTest extends LightJavaCodeInsightFixtureTestCase {
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
                                                     "  public static void <caret>main(String[] args) { someCode(); }" +
                                                     "}");
    assertSize(1, myFixture.findGuttersAtCaret());

    ShowIntentionsPass.IntentionsInfo intentions = ShowIntentionsPass.getActionsToShow(getEditor(), getFile(), false);
    assertThat(intentions.guttersToShow.size()).isGreaterThan(1);
  }

  public void testRunLineMarker() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", "public class Main<caret>Test extends junit.framework.TestCase {\n" +
                                               "    public void testFoo() {\n" +
                                               "    }\n" +
                                               "}");
    myFixture.doHighlighting();
    CachedIntentions intentions = IntentionsUI.getInstance(getProject()).getCachedIntentions(getEditor(), getFile());
    intentions.wrapAndUpdateGutters();
    assertThat(intentions.getAllActions().get(0).getText()).startsWith("Run ");
  }

  public void testDoNotIncludeActionGroup() {
    myFixture.configureByText(JavaFileType.INSTANCE, "public class Foo {\n" +
                                                     "  public static void <caret>main(String[] args) { someCode(); }" +
                                                     "}");
    assertSize(1, myFixture.findGuttersAtCaret());

    ShowIntentionsPass.IntentionsInfo intentions = ShowIntentionsPass.getActionsToShow(getEditor(), getFile(), false);
    List<AnAction> descriptors = intentions.guttersToShow;
    Set<String> names = descriptors.stream().map(o -> o.getTemplatePresentation().getText()).collect(Collectors.toSet());
    assertEquals(descriptors.size(), names.size());
  }

  public void testFixesOnTop() {
    myFixture.configureByText(JavaFileType.INSTANCE, "public class Foo extends Bo<caret>o {\n" +
                                                     "  public static void main(String[] args) {}" +
                                                     "}");
    List<IntentionAction> actions = myFixture.getAvailableIntentions();
    assertThat(actions.get(0).getText()).startsWith("Create class ");
  }

  public void testWarningFixesOnTop() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", "public class MainTest extends junit.framework.TestCase {\n" +
                                               "    public void testFoo() throws Exce<caret>ption {\n" +
                                               "    }\n" +
                                               "}");
    myFixture.enableInspections(new RedundantThrowsDeclarationLocalInspection());
    myFixture.doHighlighting();
    List<IntentionAction> actions = myFixture.getAvailableIntentions();
    assertThat(actions.get(0).getText()).startsWith("Remove ");
  }
}
