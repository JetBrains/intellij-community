// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.search;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class FindUsagesTargetTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testRecordClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", "record X<caret>yz() {}");
    ReadAction.runBlocking(() -> {
      PsiElement element = getTargetElement();
      assertTrue(element instanceof PsiClass);
      assertTrue(((PsiClass)element).isRecord());
    });
  }

  @Test
  public void testRecordConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", "record Xyz<caret>() {}");
    ReadAction.runBlocking(() -> {
      PsiElement element = getTargetElement();
      assertTrue(element instanceof PsiMethod);
      assertTrue(((PsiMethod)element).isConstructor());
      assertEquals("Xyz", ((PsiMethod)element).getName());
    });
  }

  @Test
  public void testRecordConstructor2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", "record Xyz()<caret> {}");
    ReadAction.runBlocking(() -> {
      PsiElement element = getTargetElement();
      assertTrue(element instanceof PsiMethod);
      assertTrue(((PsiMethod)element).isConstructor());
      assertEquals("Xyz", ((PsiMethod)element).getName());
    });
  }

  @Test
  public void testRecordConstructor3() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", "record Xyz <caret>() {}");
    ReadAction.runBlocking(() -> {
      PsiElement element = getTargetElement();
      assertTrue(element instanceof PsiMethod);
      assertTrue(((PsiMethod)element).isConstructor());
      assertEquals("Xyz", ((PsiMethod)element).getName());
    });
  }
  
  @Test
  public void testDefaultConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("OneLeggedDog.java", """
      public class OneLeggedDog {
        static void main(){
          OneLeggedDog dog = new <caret>OneLeggedDog();
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiElement element = getTargetElement();
      assertTrue(element instanceof PsiMethod m && m.isConstructor() && "OneLeggedDog".equals(m.getName()));
    });
  }

  private PsiElement getTargetElement() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return ReadAction.computeBlocking(() -> {
      DataContext dataContext = ((EditorEx)myFixture.getEditor()).getDataContext();
      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
      PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);

      UsageTarget[] targets = UsageTargetUtil.findUsageTargets(editor, psiFile, psiElement);
      assertTrue(targets.length > 0, "<caret> is ");
      assertTrue(targets[0] instanceof PsiElementUsageTarget);
      return ((PsiElementUsageTarget)targets[0]).getElement();
    });
  }
}
