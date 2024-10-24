// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public final class BytecodeAccessorTest extends LightJavaCodeInsightFixtureTestCase {
  public void testAccessors() {
    PsiClass psiClass =
      JavaPsiFacade.getInstance(getProject()).findClass("java.util.OptionalInt", GlobalSearchScope.allScope(getProject()));
    assertNotNull(psiClass);
    assertInstanceOf(psiClass, PsiCompiledElement.class);
    PsiMethod isPresentMethod = psiClass.findMethodsByName("isPresent", false)[0];
    assertFalse(isPresentMethod.hasModifierProperty(PsiModifier.STATIC));
    PsiMethod emptyMethod = psiClass.findMethodsByName("empty", false)[0];
    assertTrue(emptyMethod.hasModifierProperty(PsiModifier.STATIC));
    ProjectBytecodeAnalysis analysis = ProjectBytecodeAnalysis.getInstance(getProject());
    PsiField isPresentField = analysis.findFieldForGetter(isPresentMethod);
    assertNotNull(isPresentField);
    PsiField emptyField = analysis.findFieldForGetter(emptyMethod);
    assertNotNull(emptyField);
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }
}
