// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class PostfixTemplatesLoadingUnloadingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
  }

  @Test
  public void testLoadUnload() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("dummy.java", """
      package templates;

      public class Foo {
          void m(boolean b, int value) {
              b.<caret>
              value = 123;
          }
      }""");
    LookupElement[] elements = myFixture.completeBasic();
    List<String> actual = ContainerUtil.map(elements, element -> element.getLookupString());
    assertThat(actual).doesNotContain(".hello");
    
    //add extension
    MyPostfixTemplateProvider templateProvider = new MyPostfixTemplateProvider();
    LanguagePostfixTemplate.LANG_EP.addExplicitExtension(JavaLanguage.INSTANCE, templateProvider);
    elements = myFixture.completeBasic();
    actual = ContainerUtil.map(elements, element -> element.getLookupString());
    assertThat(actual).contains(".hello");
    
    //remove extension
    LanguagePostfixTemplate.LANG_EP.removeExplicitExtension(JavaLanguage.INSTANCE, templateProvider);
    elements = myFixture.completeBasic();
    actual = ContainerUtil.map(elements, element -> element.getLookupString());
    assertThat(actual).doesNotContain(".hello");
  }

  private static class MyPostfixTemplateProvider implements PostfixTemplateProvider {

    private final Set<PostfixTemplate> mySingleton = Collections.singleton(new MyPostfixTemplate(this));

    private static class MyPostfixTemplate extends PostfixTemplate {

      protected MyPostfixTemplate(@Nullable PostfixTemplateProvider provider) {
        super("hello", "hello", "hello", provider);
      }

      @Override
      public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
        return true;
      }

      @Override
      public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
        //do nothing
      }
    }

    @NotNull
    @Override
    public Set<PostfixTemplate> getTemplates() {
      
      return mySingleton;
    }

    @Override
    public boolean isTerminalSymbol(char currentChar) {
      return false;
    }

    @Override
    public void preExpand(@NotNull PsiFile file, @NotNull Editor editor) {
      //
    }

    @Override
    public void afterExpand(@NotNull PsiFile file, @NotNull Editor editor) {
      //
    }

    @NotNull
    @Override
    public PsiFile preCheck(@NotNull PsiFile copyFile, @NotNull Editor realEditor, int currentOffset) {
      return copyFile;
    }
  }
}
