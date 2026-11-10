// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.IntentionsUI;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.intention.AdvertisementAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.impl.AssignShortcutToIntentionAction;
import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.codeInsight.intention.impl.EditIntentionSettingsAction;
import com.intellij.codeInsight.intention.impl.EnableDisableIntentionAction;
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclarationLocalInspection;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Dmitry Avdeev
 */
@TestApplication
public class GutterIntentionsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testEmptyIntentions() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Foo {
        <caret>   private String test() {
              return null;
           }}""");
    myFixture.findAllGutters();
    List<IntentionAction> intentions =
      myFixture.getAvailableIntentions().stream()
        .map(IntentionActionDelegate::unwrap)
        .filter(action -> !(action instanceof AdvertisementAction))
        .filter(action -> !(action instanceof EditIntentionSettingsAction))
        .filter(action -> !(action instanceof EnableDisableIntentionAction))
        .filter(action -> !(action instanceof AssignShortcutToIntentionAction))
        .toList();
    Assertions.assertThat(intentions).isNullOrEmpty();
  }

  @Test
  public void testOptions() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, "public class Foo {\n" +
                                                     "  public static void <caret>main(String[] args) { someCode(); }" +
                                                     "}");
    assertThat(myFixture.findGuttersAtCaret()).hasSize(1);

    ShowIntentionsPass.IntentionsInfo intentions =
      ReadAction.computeBlocking(() -> ShowIntentionsPass.getActionsToShow(myFixture.getEditor(), myFixture.getFile()));
    assertThat(intentions.guttersToShow.size()).isGreaterThan(1);
  }

  @Test
  public void testRunLineMarker() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", """
      public class Main<caret>Test extends junit.framework.TestCase {
          public void testFoo() {
          }
      }""");
    myFixture.doHighlighting();
    CachedIntentions intentions = IntentionsUI.getInstance(myFixture.getProject()).getCachedIntentions(myFixture.getEditor(), myFixture.getFile());
    ReadAction.runBlocking(() -> {
      intentions.wrapAndUpdateGutters();
      assertThat(intentions.getAllActions().get(0).getText()).startsWith("Run ");
    });
  }

  @Test
  public void testDoNotIncludeActionGroup() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, "public class Foo {\n" +
                                                     "  public static void <caret>main(String[] args) { someCode(); }" +
                                                     "}");
    assertThat(myFixture.findGuttersAtCaret()).hasSize(1);

    ShowIntentionsPass.IntentionsInfo intentions =
      ReadAction.computeBlocking(() -> ShowIntentionsPass.getActionsToShow(myFixture.getEditor(), myFixture.getFile()));
    List<AnAction> descriptors = intentions.guttersToShow;
    Set<String> names = descriptors.stream().map(o -> o.getTemplatePresentation().getText()).collect(Collectors.toSet());
    assertEquals(descriptors.size(), names.size());
  }

  @Test
  public void testFixesOnTop() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, "public class Foo extends Bo<caret>o {\n" +
                                                     "  public static void main(String[] args) {}" +
                                                     "}");
    List<IntentionAction> actions = myFixture.getAvailableIntentions();
    assertThat(actions.get(0).getText()).startsWith("Create class ");
  }

  @Test
  public void testWarningFixesOnTop() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", """
      public class MainTest extends junit.framework.TestCase {
          public void testFoo() throws Exce<caret>ption {
          }
      }""");
    myFixture.enableInspections(new RedundantThrowsDeclarationLocalInspection());
    myFixture.doHighlighting();
    List<IntentionAction> actions = myFixture.getAvailableIntentions();
    assertThat(actions.get(0).getText()).startsWith("Remove ");
  }
}
