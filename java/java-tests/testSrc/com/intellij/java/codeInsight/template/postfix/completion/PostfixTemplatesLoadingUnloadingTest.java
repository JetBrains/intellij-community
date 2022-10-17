// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PostfixTemplatesLoadingUnloadingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable());
  }
  
  
  public void testLoadUnload() {
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
    assertDoesntContain(actual, ".hello");
    
    //add extension
    MyPostfixTemplateProvider templateProvider = new MyPostfixTemplateProvider();
    LanguagePostfixTemplate.LANG_EP.addExplicitExtension(JavaLanguage.INSTANCE, templateProvider);
    elements = myFixture.completeBasic();
    actual = ContainerUtil.map(elements, element -> element.getLookupString());
    assertContainsElements(actual, ".hello");
    
    //remove extension
    LanguagePostfixTemplate.LANG_EP.removeExplicitExtension(JavaLanguage.INSTANCE, templateProvider);
    elements = myFixture.completeBasic();
    actual = ContainerUtil.map(elements, element -> element.getLookupString());
    assertDoesntContain(actual, ".hello");
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
