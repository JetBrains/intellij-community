// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.search;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetUtil;
import org.jetbrains.annotations.NotNull;

public class FindUsagesTargetTest extends LightJavaCodeInsightFixtureTestCase {
  public void testRecordClass() {
    myFixture.configureByText("Test.java", "record X<caret>yz() {}");
    PsiElement element = getTargetElement();
    assertTrue(element instanceof PsiClass);
    assertTrue(((PsiClass)element).isRecord());
  }

  public void testRecordConstructor() {
    myFixture.configureByText("Test.java", "record Xyz<caret>() {}");
    PsiElement element = getTargetElement();
    assertTrue(element instanceof PsiMethod);
    assertTrue(((PsiMethod)element).isConstructor());
    assertEquals("Xyz", ((PsiMethod)element).getName());
  }

  public void testRecordConstructor2() {
    myFixture.configureByText("Test.java", "record Xyz()<caret> {}");
    PsiElement element = getTargetElement();
    assertTrue(element instanceof PsiMethod);
    assertTrue(((PsiMethod)element).isConstructor());
    assertEquals("Xyz", ((PsiMethod)element).getName());
  }

  public void testRecordConstructor3() {
    myFixture.configureByText("Test.java", "record Xyz <caret>() {}");
    PsiElement element = getTargetElement();
    assertTrue(element instanceof PsiMethod);
    assertTrue(((PsiMethod)element).isConstructor());
    assertEquals("Xyz", ((PsiMethod)element).getName());
  }

  private PsiElement getTargetElement() {
    DataContext dataContext = ((EditorEx)myFixture.getEditor()).getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);

    UsageTarget[] targets = UsageTargetUtil.findUsageTargets(editor, psiFile, psiElement);
    assertTrue(targets.length > 0);
    assertTrue(targets[0] instanceof PsiElementUsageTarget);
    return ((PsiElementUsageTarget)targets[0]).getElement();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }
}
