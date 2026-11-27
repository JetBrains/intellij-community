// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.navigation;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.codeInsight.navigation.ClassImplementationsSearch;
import com.intellij.codeInsight.navigation.MethodImplementationsSearch;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.CommonProcessors;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class GotoImplementationTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(
    JavaTestUtil.getJavaTestDataPath(), ignored -> JavaTestUtil.getJavaTestDataPath() + "/codeInsight/navigation/alexProject");

  private static Collection<PsiElement> getClassImplementations(final PsiClass psiClass) {
    CommonProcessors.CollectProcessor<PsiElement> processor = new CommonProcessors.CollectProcessor<>();
    ClassImplementationsSearch.processImplementations(psiClass, processor, psiClass.getUseScope());

    return processor.getResults();
  }

  @Test
  public void test() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    JavaPsiFacade javaFacade = fixture.getJavaFacade();
    ReadAction.runBlocking(() -> {
      ModuleManager moduleManager = ModuleManager.getInstance(fixture.getProject());
      Module[] modules = moduleManager.getModules();
      assertEquals(3, modules.length);

      Module module1 = moduleManager.findModuleByName("test1");
      Module module2 = moduleManager.findModuleByName("test2");
      Module module3 = moduleManager.findModuleByName("test3");
      final GlobalSearchScope module1Scope = GlobalSearchScope.moduleScope(module1);
      PsiClass test1 = javaFacade.findClass("com.test.TestI", module1Scope);
      PsiClass test2 = javaFacade.findClass("com.test.TestI", GlobalSearchScope.moduleScope(module2));
      PsiClass test3 = javaFacade.findClass("com.test.TestI", GlobalSearchScope.moduleScope(module3));
      HashSet<PsiElement> expectedImpls1 = new HashSet<>(Arrays.asList(
        javaFacade.findClass("com.test.TestIImpl1", module1Scope),
        javaFacade.findClass("com.test.TestIImpl2", module1Scope)
      ));
      assertEquals(expectedImpls1, new HashSet<>(getClassImplementations(test1)));

      PsiMethod psiMethod = test1.findMethodsByName("test", false)[0];
      Set<PsiElement> expectedMethodImpl1 = new HashSet<>(Arrays.asList(
        javaFacade.findClass("com.test.TestIImpl1", module1Scope).findMethodsByName("test", false)[0],
        javaFacade.findClass("com.test.TestIImpl2", module1Scope).findMethodsByName("test", false)[0]
      ));
      CommonProcessors.CollectProcessor<PsiElement> processor = new CommonProcessors.CollectProcessor<>();
      MethodImplementationsSearch.processImplementations(psiMethod, processor, module1Scope);
      assertEquals(expectedMethodImpl1, new HashSet<>(processor.getResults()));

      HashSet<PsiElement> expectedImpls2 = new HashSet<>(Arrays.asList(
        javaFacade.findClass("com.test.TestIImpl1", GlobalSearchScope.moduleScope(module2)),
        javaFacade.findClass("com.test.TestIImpl3", GlobalSearchScope.moduleScope(module2))
      ));
      assertEquals(expectedImpls2, new HashSet<>(getClassImplementations(test2)));

      HashSet<PsiElement> expectedImpls3 = new HashSet<>(Arrays.asList(
        javaFacade.findClass("com.test.TestIImpl1", GlobalSearchScope.moduleScope(module3))
      ));
      assertEquals(expectedImpls3, new HashSet<>(getClassImplementations(test3)));
    });
  }
}
