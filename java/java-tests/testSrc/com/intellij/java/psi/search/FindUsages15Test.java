// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.search;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class FindUsages15Test {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    IdeaTestUtil.setProjectLanguageLevel(fixture.getProject(), LanguageLevel.JDK_1_5);
    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/findUsages15/" + fixture.getTestName(true);
    PsiTestUtil.removeAllRoots(fixture.getModule(), IdeaTestUtil.getMockJdk17());
    fixture.createTestProjectStructure(root);
  }

  @Test
  public void testEnumConstructor() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    JavaPsiFacade myJavaFacade = fixture.getJavaFacade();
    Module myModule = fixture.getModule();
    ReadAction.runBlocking(() -> {
      PsiClass enumClass = myJavaFacade.findClass("pack.OurEnum", GlobalSearchScope.moduleScope(myModule));
      assertNotNull(enumClass);
      assertTrue(enumClass.isEnum());
      PsiMethod[] constructors = enumClass.getConstructors();
      assertEquals(2, constructors.length);
      PsiReference[] references0 =
        ReferencesSearch.search(constructors[0], GlobalSearchScope.moduleScope(myModule), false).toArray(PsiReference.EMPTY_ARRAY);
      assertEquals(2, references0.length);
      assertInstanceOf(PsiEnumConstant.class, references0[0].getElement());
      assertInstanceOf(PsiEnumConstant.class, references0[1].getElement());
      PsiReference[] references1 =
        ReferencesSearch.search(constructors[1], GlobalSearchScope.moduleScope(myModule), false).toArray(PsiReference.EMPTY_ARRAY);
      assertEquals(1, references1.length);
      assertInstanceOf(PsiEnumConstant.class, references1[0].getElement());
    });
  }

  @Test
  public void testGenericMethodOverriderUsages () {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    JavaPsiFacade myJavaFacade = fixture.getJavaFacade();
    Module myModule = fixture.getModule();
    ReadAction.runBlocking(() -> {
      final PsiClass baseClass = myJavaFacade.findClass("pack.GenericClass", GlobalSearchScope.moduleScope(myModule));
      assertNotNull(baseClass);
      final PsiMethod method = baseClass.getMethods()[0];
      PsiReference[] references =
        MethodReferencesSearch.search(method, GlobalSearchScope.moduleScope(myModule), false).toArray(PsiReference.EMPTY_ARRAY);
      assertEquals(1, references.length);
      final PsiElement element = references[0].getElement();
      final PsiClass refClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      assertEquals("GenericClassDerived", refClass.getName());
    });
  }

  @Test
  public void testFindRawOverriddenUsages () {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    JavaPsiFacade myJavaFacade = fixture.getJavaFacade();
    Module myModule = fixture.getModule();
    ReadAction.runBlocking(() -> {
      final PsiClass baseClass = myJavaFacade.findClass("pack.Base", GlobalSearchScope.moduleScope(myModule));
      assertNotNull(baseClass);
      final PsiMethod method = baseClass.getMethods()[0];
      PsiMethod[] overriders =
        OverridingMethodsSearch.search(method, GlobalSearchScope.moduleScope(myModule), true).toArray(PsiMethod.EMPTY_ARRAY);
      assertEquals(1, overriders.length);
    });
  }
  @Test
  public void testGenericOverride() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    JavaPsiFacade myJavaFacade = fixture.getJavaFacade();
    Module myModule = fixture.getModule();
    ReadAction.runBlocking(() -> {
      final PsiClass baseClass = myJavaFacade.findClass("pack.Gen", GlobalSearchScope.moduleScope(myModule));
      assertNotNull(baseClass);
      final PsiMethod method = baseClass.getMethods()[0];
      PsiReference[] references =
        MethodReferencesSearch.search(method, GlobalSearchScope.projectScope(fixture.getProject()), true).toArray(PsiReference.EMPTY_ARRAY);

      assertEquals(1, references.length);

      PsiClass refClass = PsiTreeUtil.getParentOfType(references[0].getElement(), PsiClass.class);
      assertEquals("X2", refClass.getName());
    });
  }
}
