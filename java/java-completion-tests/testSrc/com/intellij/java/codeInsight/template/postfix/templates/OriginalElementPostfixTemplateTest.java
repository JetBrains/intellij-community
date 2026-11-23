// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateStorage;
import com.intellij.codeInsight.template.postfix.templates.JavaPostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.LazyKt;
import kotlin.Lazy;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.intellij.java.codeInsight.template.postfix.templates.PostfixTemplateFixtureKt.postfixTemplateFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static java.util.Arrays.asList;

@TestApplication
@IndexingModeTemplate
public class OriginalElementPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "originalElement");

  private static final Lazy<JavaPostfixTemplateProvider> PROVIDER = LazyKt.lazyPub(() -> new JavaPostfixTemplateProvider());

  private Set<PostfixTemplate> myOriginalTemplates;

  @BeforeEach
  void setUp() {
    JavaPostfixTemplateProvider provider = PROVIDER.getValue();
    myOriginalTemplates = PostfixTemplateStorage.getInstance().getTemplates(provider);
    // Register a custom condition which returns true if it can get an original element of the next method.
    // This emulates conditions in some languages e.g. in go where resolve involves getOriginalElement() calls.
    PostfixTemplate template = new JavaEditablePostfixTemplate(
      "myId", "foo", "System.out.println();$END$", "",
      Set.of(new OriginalElementCondition()),
      LanguageLevel.JDK_1_8, true, provider);
    PostfixTemplateStorage.getInstance().setTemplates(provider, asList(template));
  }

  @AfterEach
  void tearDown() {
    PostfixTemplateStorage.getInstance().setTemplates(PROVIDER.getValue(), myOriginalTemplates);
  }

  @NeedsIndex.SmartMode(reason = "Not created as DumbAware")
  @Test
  public void testOriginalElement() {
    doTest();
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

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> postfix.get().doTest());
  }
}
