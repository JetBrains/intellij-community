// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public final class BytecodeAccessorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testGetters() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      PsiClass psiClass =
        JavaPsiFacade.getInstance(myFixture.getProject()).findClass("java.util.OptionalInt", GlobalSearchScope.allScope(myFixture.getProject()));
      assertNotNull(psiClass);
      assertInstanceOf(PsiCompiledElement.class, psiClass);
      PsiMethod isPresentMethod = psiClass.findMethodsByName("isPresent", false)[0];
      assertFalse(isPresentMethod.hasModifierProperty(PsiModifier.STATIC));
      PsiMethod emptyMethod = psiClass.findMethodsByName("empty", false)[0];
      assertTrue(emptyMethod.hasModifierProperty(PsiModifier.STATIC));
      ProjectBytecodeAnalysis analysis = ProjectBytecodeAnalysis.getInstance(myFixture.getProject());
      PsiField isPresentField = analysis.findFieldForAccessor(isPresentMethod);
      assertNotNull(isPresentField);
      assertEquals("isPresent", isPresentField.getName());
      PsiField emptyField = analysis.findFieldForAccessor(emptyMethod);
      assertNotNull(emptyField);
      assertEquals("EMPTY", emptyField.getName());
    });
  }

  @Test
  public void testSetters() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      PsiClass psiClass =
        JavaPsiFacade.getInstance(myFixture.getProject()).findClass("java.text.MessageFormat", GlobalSearchScope.allScope(myFixture.getProject()));
      assertNotNull(psiClass);
      assertInstanceOf(PsiCompiledElement.class, psiClass);
      PsiMethod setLocaleMethod = psiClass.findMethodsByName("setLocale", false)[0];
      assertFalse(setLocaleMethod.hasModifierProperty(PsiModifier.STATIC));
      ProjectBytecodeAnalysis analysis = ProjectBytecodeAnalysis.getInstance(myFixture.getProject());
      PsiField localeField = analysis.findFieldForAccessor(setLocaleMethod);
      assertNotNull(localeField);
      assertEquals("locale", localeField.getName());
    });
  }
}
