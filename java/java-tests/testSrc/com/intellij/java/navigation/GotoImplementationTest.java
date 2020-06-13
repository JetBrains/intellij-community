// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.navigation;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.codeInsight.navigation.ClassImplementationsSearch;
import com.intellij.codeInsight.navigation.MethodImplementationsSearch;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.CommonProcessors;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class GotoImplementationTest extends JavaCodeInsightTestCase {

  private static Collection<PsiElement> getClassImplementations(final PsiClass psiClass) {
    CommonProcessors.CollectProcessor<PsiElement> processor = new CommonProcessors.CollectProcessor<>();
    ClassImplementationsSearch.processImplementations(psiClass, processor, psiClass.getUseScope());

    return processor.getResults();
  }

  @Override
  protected void setUpProject() {
    String root = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/navigation/alexProject";
    VirtualFile vfsRoot = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(root));
    myProject = PlatformTestUtil.loadAndOpenProject(Paths.get(vfsRoot.findChild("test.ipr").getPath()));
  }

  public void test() {
    ModuleManager moduleManager = ModuleManager.getInstance(getProject());
    Module[] modules = moduleManager.getModules();
    assertEquals(3, modules.length);

    Module module1 = moduleManager.findModuleByName("test1");
    Module module2 = moduleManager.findModuleByName("test2");
    Module module3 = moduleManager.findModuleByName("test3");
    final GlobalSearchScope module1Scope = GlobalSearchScope.moduleScope(module1);
    PsiClass test1 = myJavaFacade.findClass("com.test.TestI", module1Scope);
    PsiClass test2 = myJavaFacade.findClass("com.test.TestI", GlobalSearchScope.moduleScope(module2));
    PsiClass test3 = myJavaFacade.findClass("com.test.TestI", GlobalSearchScope.moduleScope(module3));
    HashSet<PsiElement> expectedImpls1 = new HashSet<>(Arrays.asList(
      myJavaFacade.findClass("com.test.TestIImpl1", module1Scope),
      myJavaFacade.findClass("com.test.TestIImpl2", module1Scope)
    ));
    assertEquals(expectedImpls1, new HashSet<>(getClassImplementations(test1)));

    PsiMethod psiMethod = test1.findMethodsByName("test", false)[0];
    Set<PsiElement> expectedMethodImpl1 = new HashSet<>(Arrays.asList(
      myJavaFacade.findClass("com.test.TestIImpl1", module1Scope).findMethodsByName("test", false)[0],
      myJavaFacade.findClass("com.test.TestIImpl2", module1Scope).findMethodsByName("test", false)[0]
    ));
    CommonProcessors.CollectProcessor<PsiElement> processor = new CommonProcessors.CollectProcessor<>();
    MethodImplementationsSearch.processImplementations(psiMethod, processor, module1Scope);
    assertEquals(expectedMethodImpl1, new HashSet<>(processor.getResults()));

    HashSet<PsiElement> expectedImpls2 = new HashSet<>(Arrays.asList(
      myJavaFacade.findClass("com.test.TestIImpl1", GlobalSearchScope.moduleScope(module2)),
      myJavaFacade.findClass("com.test.TestIImpl3", GlobalSearchScope.moduleScope(module2))
    ));
    assertEquals(expectedImpls2, new HashSet<>(getClassImplementations(test2)));

    HashSet<PsiElement> expectedImpls3 = new HashSet<>(Arrays.asList(
      myJavaFacade.findClass("com.test.TestIImpl1", GlobalSearchScope.moduleScope(module3))
    ));
    assertEquals(expectedImpls3, new HashSet<>(getClassImplementations(test3)));
  }
}
