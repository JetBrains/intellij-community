// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateStorage;
import com.intellij.codeInsight.template.postfix.templates.JavaPostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.LazyKt;
import kotlin.Lazy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.intellij.java.codeInsight.template.postfix.templates.PostfixTemplateFixtureKt.postfixTemplateFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static java.util.Arrays.asList;

@TestApplication
@IndexingModeTemplate
public class SameKeyPostfixTemplatesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "editable");

  private static final Lazy<JavaPostfixTemplateProvider> PROVIDER = LazyKt.lazyPub(() -> new JavaPostfixTemplateProvider());

  @BeforeEach
  void setUp() {
    JavaPostfixTemplateProvider provider = PROVIDER.getValue();
    PostfixTemplate template1 = new JavaEditablePostfixTemplate(
      "myId1", "sameKey", "Boolean.toString($EXPR$);$END$", "",
      Set.of(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateBooleanExpressionCondition()),
      LanguageLevel.JDK_1_8, true, provider);
    PostfixTemplate template2 = new JavaEditablePostfixTemplate(
      "myId2", "sameKey", "Integer.toString($EXPR$);$END$", "",
      Set.of(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNumberExpressionCondition()),
      LanguageLevel.JDK_1_8, true, provider);
    PostfixTemplateStorage.getInstance().setTemplates(provider, asList(template1, template2));
  }

  @NeedsIndex.SmartMode(reason = "Not created as DumbAware")
  @Test
  public void testSameKeyInteger() {
    doTest();
  }

  @NeedsIndex.SmartMode(reason = "Not created as DumbAware")
  @Test
  public void testSameKeyBoolean() {
    doTest();
  }

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> postfix.get().doTest());
  }
}
