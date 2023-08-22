// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateStorage;
import com.intellij.codeInsight.template.postfix.templates.JavaPostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.LazyKt;
import kotlin.Lazy;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static java.util.Arrays.asList;

public class SameKeyPostfixTemplatesTest extends PostfixTemplateTestCase {
  private static final Lazy<JavaPostfixTemplateProvider> PROVIDER = LazyKt.lazyPub(() -> new JavaPostfixTemplateProvider());

  @Override
  public void setUp() throws Exception {
    super.setUp();

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

  public void testSameKeyInteger() {
    doTest();
  }

  public void testSameKeyBoolean() {
    doTest();
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "editable";
  }
}
