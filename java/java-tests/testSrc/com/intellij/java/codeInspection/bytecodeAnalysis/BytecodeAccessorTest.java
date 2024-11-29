// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public final class BytecodeAccessorTest extends LightJavaCodeInsightFixtureTestCase {
  public void testGetters() {
    PsiClass psiClass =
      JavaPsiFacade.getInstance(getProject()).findClass("java.util.OptionalInt", GlobalSearchScope.allScope(getProject()));
    assertNotNull(psiClass);
    assertInstanceOf(psiClass, PsiCompiledElement.class);
    PsiMethod isPresentMethod = psiClass.findMethodsByName("isPresent", false)[0];
    assertFalse(isPresentMethod.hasModifierProperty(PsiModifier.STATIC));
    PsiMethod emptyMethod = psiClass.findMethodsByName("empty", false)[0];
    assertTrue(emptyMethod.hasModifierProperty(PsiModifier.STATIC));
    ProjectBytecodeAnalysis analysis = ProjectBytecodeAnalysis.getInstance(getProject());
    PsiField isPresentField = analysis.findFieldForAccessor(isPresentMethod);
    assertNotNull(isPresentField);
    assertEquals("isPresent", isPresentField.getName());
    PsiField emptyField = analysis.findFieldForAccessor(emptyMethod);
    assertNotNull(emptyField);
    assertEquals("EMPTY", emptyField.getName());
  }

  public void testSetters() {
    PsiClass psiClass =
      JavaPsiFacade.getInstance(getProject()).findClass("java.text.MessageFormat", GlobalSearchScope.allScope(getProject()));
    assertNotNull(psiClass);
    assertInstanceOf(psiClass, PsiCompiledElement.class);
    PsiMethod setLocaleMethod = psiClass.findMethodsByName("setLocale", false)[0];
    assertFalse(setLocaleMethod.hasModifierProperty(PsiModifier.STATIC));
    ProjectBytecodeAnalysis analysis = ProjectBytecodeAnalysis.getInstance(getProject());
    PsiField localeField = analysis.findFieldForAccessor(setLocaleMethod);
    assertNotNull(localeField);
    assertEquals("locale", localeField.getName());
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }
}
