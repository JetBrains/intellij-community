// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionItemLookupElement;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaCompletionAutoPopupFixture;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.codeInsight.template.postfix.templates.ForAscendingPostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.InstanceofExpressionPostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.JavaPostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.NotNullCheckPostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.SwitchStatementPostfixTemplate;
import com.intellij.idea.TestFor;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static com.intellij.codeInsight.completion.JavaCompletionAutoPopupFixtureKt.javaCompletionAutoPopupFixture;
import static com.intellij.testFramework.UsefulTestCase.assertEmpty;
import static com.intellij.testFramework.UsefulTestCase.assertInstanceOf;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@NeedsIndex.SmartMode(reason = "AutoPopup shouldn't work in dumb mode")
@TestApplication
@IndexingModeTemplate
public class TemplatesCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<JavaCompletionAutoPopupFixture> autoPopupFixture = javaCompletionAutoPopupFixture(projectFixture);
  private LightJavaCodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = autoPopupFixture.get().getFixture();
    myFixture.setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/template/postfix/completion");
    LiveTemplateCompletionContributor.setShowTemplatesInTests(false, myFixture.getTestRootDisposable());
  }

  @AfterEach
  void tearDown() {
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    assertNotNull(settings);
    settings.setProviderToDisabledTemplates(new HashMap<>());
    settings.setPostfixTemplatesEnabled(true);
    settings.setTemplatesCompletionEnabled(true);
  }

  @Test
  public void testSimpleCompletionList() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    doAutoPopupTest("ins", InstanceofExpressionPostfixTemplate.class);
  }

  @Test
  public void testAutopopupWithEnabledLiveTemplatesInCompletion() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(false, myFixture.getTestRootDisposable());

    configureByFile();
    type("instanceof");
    LookupImpl lookup = getLookup();
    assertNotNull(lookup);
    assertEquals(1, lookup.getItems().size());
    LookupElement item = lookup.getCurrentItem();
    assertNotNull(item);
    assertInstanceOf(item, PostfixTemplateLookupElement.class);
    assertInstanceOf(((PostfixTemplateLookupElement)item).getPostfixTemplate(), InstanceofExpressionPostfixTemplate.class);
  }

  @Test
  public void testDoNotShowTemplateInInappropriateContext() {
    doAutoPopupTest("instanceof", null);
  }

  // IDEA-119910 Middle matching doesn't work if pattern starts with a digit
  @Test
  public void testRestartCompletionForExactMatchOnly() {
    doCompleteTest(".2", '\n');
  }

  @Test
  public void testRestartCompletionForExactMatch() {
    configureByFile();
    type("not");
    LookupElement currentItem = getLookup().getCurrentItem();
    assertNotNull(currentItem);
    assertInstanceOf(currentItem, PostfixTemplateLookupElement.class);
    assertEquals(".not", currentItem.getLookupString());

    type("null");
    currentItem = getLookup().getCurrentItem();
    assertNotNull(currentItem);
    assertInstanceOf(currentItem, PostfixTemplateLookupElement.class);
    assertEquals(".notnull", currentItem.getLookupString());
  }

  @Test
  public void testShowTemplateInAutoPopup() {
    doAutoPopupTest("instanceof", InstanceofExpressionPostfixTemplate.class);
  }

  @Test
  public void testShowAutoPopupForAliases() {
    doAutoPopupTest("nn", NotNullCheckPostfixTemplate.class);
  }

  @Test
  public void testShowAutoPopupForFloatLiterals() {
    doAutoPopupTest("fori", ForAscendingPostfixTemplate.class);
  }

  @Test
  public void testDoNotShowTemplateIfPluginIsDisabled() {
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    assertNotNull(settings);
    settings.setPostfixTemplatesEnabled(false);
    doAutoPopupTest("instanceof", null);
  }

  @Test
  public void testDoNotShowTemplateIfTemplateCompletionIsDisabled() {
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    assertNotNull(settings);
    settings.setTemplatesCompletionEnabled(false);
    doAutoPopupTest("instanceof", null);
  }

  @Test
  public void testDoNotShowTemplateInMultiCaretMode() {
    doAutoPopupTest("instanceof", null);
  }

  @Test
  public void testDoNotAutoCompleteCompletionElementIfTemplateUnique() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    configureByFile();
    myFixture.completeBasic();
    checkResultByFile();
  }

  @Test
  public void testDoNotCompleteTemplateInMultiCaretMode() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    configureByFile();
    assertEmpty(myFixture.complete(CompletionType.BASIC));
    checkResultByFile();
  }

  @Test
  public void testShowTemplateOnDoubleLiteral() {
    doAutoPopupTest("switch", SwitchStatementPostfixTemplate.class);
  }

  @Test
  public void testSelectTemplateByTab() {
    doCompleteTest("par", '\t');
  }

  @Test
  public void testSelectTemplateByEnter() {
    doCompleteTest("par", '\n');
  }

  @Test
  public void testQuickTypingWithTab() {
    configureByFile();
    myFixture.type("par\t");
    checkResultByFile();
  }

  @Test
  public void testDoNotShowDisabledTemplate() {
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    assertNotNull(settings);
    settings.disableTemplate(new InstanceofExpressionPostfixTemplate(), new JavaPostfixTemplateProvider().getId());
    doAutoPopupTest("instanceof", null);
  }

  @Test
  public void testDoNotShowTemplateOnCompletion() {
    configureByFile();
    myFixture.completeBasic();
    LookupElement[] elements = myFixture.getLookupElements();
    assertNotNull(elements);
    assertNull(ContainerUtil.findInstance(elements, PostfixTemplateLookupElement.class));
  }

  @Test
  public void testRecalculatePrefix() {
    configureByFile();
    type("par");
    int selectedIndex = 0;
    myFixture.assertPreferredCompletionItems(selectedIndex, ".par", "parents");

    type("\b");
    assertNotNull(getLookup());
    myFixture.assertPreferredCompletionItems(selectedIndex, "parents");

    type("r");
    myFixture.assertPreferredCompletionItems(selectedIndex, ".par", "parents");
  }

  @Test
  @TestFor(issues = "IDEA-388573")
  public void testInvalidRangeAfterDeletingPostfixDot() {
    Registry.get("postfix.template.mod.completion.enabled").setValue(true, myFixture.getTestRootDisposable());
    configureByFile();
    type(".");
    LookupImpl lookup = getLookup();
    assertNotNull(lookup);
    LookupElement currentItem = lookup.getCurrentItem();
    assertInstanceOf(currentItem, CompletionItemLookupElement.class);
    type("\b");
  }

  @Test
  public void testTabCompletionWithTemplatesInAutopopup() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());

    configureByFile();
    type(".");
    myFixture.assertPreferredCompletionItems(0, "parents");

    type("\t");
    assertNull(getLookup());
    checkResultByFile();
  }

  @Test
  public void testShouldNotExpandInMultiCaretMode() {
    configureByFile();
    type(".if\t");
    checkResultByFile();
  }

  private void doCompleteTest(String textToType, char c) {
    configureByFile();
    type(textToType);
    assertNotNull(getLookup());
    myFixture.type(c);
    checkResultByFile();
  }

  private void doAutoPopupTest(@NotNull String textToType, @Nullable Class<? extends PostfixTemplate> expectedClass) {
    configureByFile();
    type(textToType);
    LookupImpl lookup = getLookup();
    if (expectedClass != null) {
      assertNotNull(lookup);
      LookupElement item = lookup.getCurrentItem();
      assertNotNull(item);
      assertInstanceOf(item, PostfixTemplateLookupElement.class);
      assertInstanceOf(((PostfixTemplateLookupElement)item).getPostfixTemplate(), expectedClass);
    }
    else {
      assertNull(lookup);
    }
  }

  @Test
  public void testOptionallyShowingSuitableLiveTemplatesBeforeOtherCompletionSuggestions() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
    Registry.get("ide.completion.show.live.templates.on.top").setValue(true, myFixture.getTestRootDisposable());

    myFixture.configureByText("a.java", "class Foo { ps<caret> } class psvClass {}");
    type("v");
    myFixture.assertPreferredCompletionItems(0, "psvm", "psvma", "psvClass");

    myFixture.configureByText("a.xml", "CARBON C<caret>");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "CD", "CARBON");
  }


  private void configureByFile() {
    EdtTestUtil.runInEdtAndWait(() -> myFixture.configureByFile(myFixture.getTestName(true) + ".java"));
  }

  private void checkResultByFile() {
    myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.java");
  }

  private void type(String s) {
    autoPopupFixture.get().type(s);
  }

  private LookupImpl getLookup() {
    return autoPopupFixture.get().getLookup();
  }
}
