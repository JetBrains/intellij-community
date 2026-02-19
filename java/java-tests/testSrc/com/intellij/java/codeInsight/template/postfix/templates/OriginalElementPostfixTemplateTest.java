// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateStorage;
import com.intellij.codeInsight.template.postfix.templates.JavaPostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.util.LazyKt;
import kotlin.Lazy;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static java.util.Arrays.asList;

public class OriginalElementPostfixTemplateTest extends PostfixTemplateTestCase {
  private static final Lazy<JavaPostfixTemplateProvider> PROVIDER = LazyKt.lazyPub(() -> new JavaPostfixTemplateProvider());

  private Set<PostfixTemplate> myOriginalTemplates;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    JavaPostfixTemplateProvider provider = PROVIDER.getValue();
    myOriginalTemplates = provider.getTemplates();
    // Register a custom condition which returns true if it can get an original element of the next method.
    // This emulates conditions in some languages e.g. in go where resolve involves getOriginalElement() calls.
    PostfixTemplate template = new JavaEditablePostfixTemplate(
      "myId", "foo", "System.out.println();$END$", "",
      Set.of(new OriginalElementCondition()),
      LanguageLevel.JDK_1_8, true, provider);
    PostfixTemplateStorage.getInstance().setTemplates(provider, asList(template));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      PostfixTemplateStorage.getInstance().setTemplates(PROVIDER.getValue(), myOriginalTemplates);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @NeedsIndex.SmartMode(reason = "Not created as DumbAware")
  public void testOriginalElement() {
    doTest();
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "originalElement";
  }

  private static class OriginalElementCondition implements JavaPostfixTemplateExpressionCondition {
    @Override
    public @NotNull @Nls String getPresentableName() {
      return "customCondition";
    }

    @NotNull
    @Override
    public String getId() {
      return "customCondition";
    }

    @Override
    public boolean value(@NotNull PsiExpression expression) {
      PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      PsiMethod nextMethod = PsiTreeUtil.getNextSiblingOfType(method, PsiMethod.class);
      return nextMethod != null && CompletionUtilCoreImpl.getOriginalElement(nextMethod) != null;
    }

    @Override
    public void serializeTo(@NotNull Element element) {
    }
  }
}
