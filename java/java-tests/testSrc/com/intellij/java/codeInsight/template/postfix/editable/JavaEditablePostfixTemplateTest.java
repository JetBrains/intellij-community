// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template.postfix.editable;

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateStorage;
import com.intellij.codeInsight.template.postfix.templates.AssertStatementPostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.JavaPostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.NotExpressionPostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixChangedBuiltinTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateWrapper;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class JavaEditablePostfixTemplateTest extends LightPlatformTestCase {
  private static final JavaPostfixTemplateProvider PROVIDER = new JavaPostfixTemplateProvider();

  public void testId() {
    JavaEditablePostfixTemplate template = new JavaEditablePostfixTemplate("myId", "myKey", "", "", Collections.emptySet(),
                                                                           LanguageLevel.JDK_1_8, true, PROVIDER);
    assertEquals("myId", reloadJavaTemplate(template).getId());
  }

  public void testTemplateKey() {
    JavaEditablePostfixTemplate template = new JavaEditablePostfixTemplate("myId", "myKey", "", "", Collections.emptySet(),
                                                                           LanguageLevel.JDK_1_8, true, PROVIDER);
    assertEquals(".myKey", reloadJavaTemplate(template).getKey());
  }

  public void testChangedBuiltinTemplate() {
    JavaEditablePostfixTemplate customTemplate = new JavaEditablePostfixTemplate("myId", "myKey", "", "", Collections.emptySet(),
                                                                                 LanguageLevel.JDK_1_8, true, PROVIDER);
    AssertStatementPostfixTemplate builtinTemplate = new AssertStatementPostfixTemplate(PROVIDER);
    PostfixChangedBuiltinTemplate template = new PostfixChangedBuiltinTemplate(customTemplate, builtinTemplate);
    PostfixTemplate reloaded = reloadTemplate(template);
    assertInstanceOf(reloaded, PostfixChangedBuiltinTemplate.class);
    assertEquals("myId", reloaded.getId());
    assertEquals("assert", ((PostfixChangedBuiltinTemplate)reloaded).getBuiltinTemplate().getId());
    assertTrue(reloaded.isBuiltin());
  }

  public void testRenamedNonEditableTemplate() {
    NotExpressionPostfixTemplate builtinTemplate = new NotExpressionPostfixTemplate();
    PostfixTemplateWrapper customTemplate = new PostfixTemplateWrapper("renamed", "renamed", ".renamed", builtinTemplate, PROVIDER);
    PostfixChangedBuiltinTemplate template = new PostfixChangedBuiltinTemplate(customTemplate, builtinTemplate);
    PostfixTemplate reloaded = reloadTemplate(template);
    assertInstanceOf(reloaded, PostfixChangedBuiltinTemplate.class);
    assertEquals("renamed", reloaded.getId());
    assertEquals(".renamed", reloaded.getKey());
    assertEquals("com.intellij.codeInsight.template.postfix.templates.NotExpressionPostfixTemplate#.not",
                 ((PostfixChangedBuiltinTemplate)reloaded).getBuiltinTemplate().getId());
    assertTrue(reloaded.isBuiltin());
  }

  public void testLanguageLevel() {
    JavaEditablePostfixTemplate template = new JavaEditablePostfixTemplate("myId", "myKey", "", "", Collections.emptySet(),
                                                                           LanguageLevel.JDK_1_8, true, PROVIDER);
    assertEquals(LanguageLevel.JDK_1_8, reloadJavaTemplate(template).getMinimumLanguageLevel());
  }

  public void testTopmost() {
    JavaEditablePostfixTemplate template = new JavaEditablePostfixTemplate("myId", "myKey", "", "", Collections.emptySet(),
                                                                           LanguageLevel.JDK_1_8, true, PROVIDER);
    assertTrue(reloadJavaTemplate(template).isUseTopmostExpression());
    template = new JavaEditablePostfixTemplate("myId", "myKey", "", "", Collections.emptySet(), LanguageLevel.JDK_1_8, false, PROVIDER);
    assertFalse(reloadJavaTemplate(template).isUseTopmostExpression());
  }

  public void testArrayCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayExpressionCondition();
    assertSameElements(reloadConditions(templateWithCondition(condition)), condition);
  }

  public void testNonVoidCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNonVoidExpressionCondition();
    assertSameElements(reloadConditions(templateWithCondition(condition)), condition);
  }

  public void testVoidCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateVoidExpressionCondition();
    assertSameElements(reloadConditions(templateWithCondition(condition)), condition);
  }

  public void testBooleanCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateBooleanExpressionCondition();
    assertSameElements(reloadConditions(templateWithCondition(condition)), condition);
  }

  public void testNumberCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNumberExpressionCondition();
    assertSameElements(reloadConditions(templateWithCondition(condition)), condition);
  }

  public void testNotPrimitiveTypeCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNotPrimitiveTypeExpressionCondition();
    assertSameElements(reloadConditions(templateWithCondition(condition)), condition);
  }

  public void testFqnCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition("test.class.Name");
    Set<JavaPostfixTemplateExpressionCondition> conditions = reloadConditions(templateWithCondition(condition));
    JavaPostfixTemplateExpressionCondition reloadedCondition = ContainerUtil.getFirstItem(conditions);
    assertSameElements(conditions, condition);
    assertEquals("test.class.Name",
                 ((JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition)reloadedCondition).getFqn());
  }

  @NotNull
  private static JavaEditablePostfixTemplate templateWithCondition(@NotNull JavaPostfixTemplateExpressionCondition condition) {
    return new JavaEditablePostfixTemplate("myId", "myKey", "", "", Collections.singleton(condition), LanguageLevel.JDK_1_8, true,
                                           PROVIDER);
  }

  @NotNull
  private static PostfixTemplate reloadTemplate(@NotNull PostfixTemplate template) {
    PostfixTemplateStorage saveStorage = new PostfixTemplateStorage();
    saveStorage.setTemplates(PROVIDER, Collections.singletonList(template));

    PostfixTemplateStorage loadStorage = PostfixTemplateStorage.getInstance();
    loadStorage.loadState(saveStorage.getState());
    return ContainerUtil.getFirstItem(loadStorage.getTemplates(PROVIDER));
  }

  @NotNull
  private static JavaEditablePostfixTemplate reloadJavaTemplate(@NotNull JavaEditablePostfixTemplate template) {
    return (JavaEditablePostfixTemplate)reloadTemplate(template);
  }

  @NotNull
  private static Set<JavaPostfixTemplateExpressionCondition> reloadConditions(@NotNull JavaEditablePostfixTemplate template) {
    return reloadJavaTemplate(template).getExpressionConditions();
  }
}
