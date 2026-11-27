// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.editable.postfix;

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
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaEditablePostfixTemplateTest {
  private static final JavaPostfixTemplateProvider PROVIDER = new JavaPostfixTemplateProvider();

  @Test
  public void testId() {
    JavaEditablePostfixTemplate template = new JavaEditablePostfixTemplate("myId", "myKey", "", "", Collections.emptySet(),
                                                                           LanguageLevel.JDK_1_8, true, PROVIDER);
    assertEquals("myId", reloadJavaTemplate(template).getId());
  }

  @Test
  public void testTemplateKey() {
    JavaEditablePostfixTemplate template = new JavaEditablePostfixTemplate("myId", "myKey", "", "", Collections.emptySet(),
                                                                           LanguageLevel.JDK_1_8, true, PROVIDER);
    assertEquals(".myKey", reloadJavaTemplate(template).getKey());
  }

  @Test
  public void testChangedBuiltinTemplate() {
    JavaEditablePostfixTemplate customTemplate = new JavaEditablePostfixTemplate("myId", "myKey", "", "", Collections.emptySet(),
                                                                                 LanguageLevel.JDK_1_8, true, PROVIDER);
    AssertStatementPostfixTemplate builtinTemplate = new AssertStatementPostfixTemplate(PROVIDER);
    PostfixChangedBuiltinTemplate template = new PostfixChangedBuiltinTemplate(customTemplate, builtinTemplate);
    PostfixTemplate reloaded = reloadTemplate(template);
    assertInstanceOf(PostfixChangedBuiltinTemplate.class, reloaded);
    assertEquals("myId", reloaded.getId());
    assertEquals("assert", ((PostfixChangedBuiltinTemplate)reloaded).getBuiltinTemplate().getId());
    assertTrue(reloaded.isBuiltin());
  }

  @Test
  public void testRenamedNonEditableTemplate() {
    NotExpressionPostfixTemplate builtinTemplate = new NotExpressionPostfixTemplate();
    PostfixTemplateWrapper customTemplate = new PostfixTemplateWrapper("renamed", "renamed", ".renamed", builtinTemplate, PROVIDER);
    PostfixChangedBuiltinTemplate template = new PostfixChangedBuiltinTemplate(customTemplate, builtinTemplate);
    PostfixTemplate reloaded = reloadTemplate(template);
    assertInstanceOf(PostfixChangedBuiltinTemplate.class, reloaded);
    assertEquals("renamed", reloaded.getId());
    assertEquals(".renamed", reloaded.getKey());
    assertEquals("com.intellij.codeInsight.template.postfix.templates.NotExpressionPostfixTemplate#.not",
                 ((PostfixChangedBuiltinTemplate)reloaded).getBuiltinTemplate().getId());
    assertTrue(reloaded.isBuiltin());
  }

  @Test
  public void testLanguageLevel() {
    JavaEditablePostfixTemplate template = new JavaEditablePostfixTemplate("myId", "myKey", "", "", Collections.emptySet(),
                                                                           LanguageLevel.JDK_1_8, true, PROVIDER);
    assertEquals(LanguageLevel.JDK_1_8, reloadJavaTemplate(template).getMinimumLanguageLevel());
  }

  @Test
  public void testTopmost() {
    JavaEditablePostfixTemplate template = new JavaEditablePostfixTemplate("myId", "myKey", "", "", Collections.emptySet(),
                                                                           LanguageLevel.JDK_1_8, true, PROVIDER);
    assertTrue(reloadJavaTemplate(template).isUseTopmostExpression());
    template = new JavaEditablePostfixTemplate("myId", "myKey", "", "", Collections.emptySet(), LanguageLevel.JDK_1_8, false, PROVIDER);
    assertFalse(reloadJavaTemplate(template).isUseTopmostExpression());
  }

  @Test
  public void testArrayCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayExpressionCondition();
    assertEquals(Set.of(condition), Set.copyOf(reloadConditions(templateWithCondition(condition))));
  }

  @Test
  public void testNonVoidCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNonVoidExpressionCondition();
    assertEquals(Set.of(condition), Set.copyOf(reloadConditions(templateWithCondition(condition))));
  }

  @Test
  public void testVoidCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateVoidExpressionCondition();
    assertEquals(Set.of(condition), Set.copyOf(reloadConditions(templateWithCondition(condition))));
  }

  @Test
  public void testBooleanCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateBooleanExpressionCondition();
    assertEquals(Set.of(condition), Set.copyOf(reloadConditions(templateWithCondition(condition))));
  }

  @Test
  public void testNumberCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNumberExpressionCondition();
    assertEquals(Set.of(condition), Set.copyOf(reloadConditions(templateWithCondition(condition))));
  }

  @Test
  public void testNotPrimitiveTypeCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNotPrimitiveTypeExpressionCondition();
    assertEquals(Set.of(condition), Set.copyOf(reloadConditions(templateWithCondition(condition))));
  }

  @Test
  public void testArrayNotPrimitiveTypeCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayReferenceExpressionCondition();
    assertEquals(Set.of(condition), Set.copyOf(reloadConditions(templateWithCondition(condition))));
  }

  @Test
  public void testFqnCondition() {
    JavaPostfixTemplateExpressionCondition condition =
      new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition("test.class.Name");
    Set<? extends JavaPostfixTemplateExpressionCondition> conditions = reloadConditions(templateWithCondition(condition));
    JavaPostfixTemplateExpressionCondition reloadedCondition = ContainerUtil.getFirstItem(conditions);
    assertEquals(Set.of(condition), Set.copyOf(conditions));
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

  private static Set<? extends JavaPostfixTemplateExpressionCondition> reloadConditions(@NotNull JavaEditablePostfixTemplate template) {
    return reloadJavaTemplate(template).getExpressionConditions();
  }
}
