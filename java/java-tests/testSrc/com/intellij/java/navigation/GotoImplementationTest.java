/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.navigation;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.navigation.ClassImplementationsSearch;
import com.intellij.codeInsight.navigation.MethodImplementationsSearch;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ui.UIUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author cdr
 */
public class GotoImplementationTest extends CodeInsightTestCase {

  private static Collection<PsiElement> getClassImplementations(final PsiClass psiClass) {
    CommonProcessors.CollectProcessor<PsiElement> processor = new CommonProcessors.CollectProcessor<>();
    ClassImplementationsSearch.processImplementations(psiClass, processor, psiClass.getUseScope());

    return processor.getResults();
  }

  @Override
  protected void setUpProject() throws Exception {
    final String root = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/navigation/alexProject";
    VirtualFile vfsRoot = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(root));

    VirtualFile projectFile = vfsRoot.findChild("test.ipr");
    myProject = ProjectManagerEx.getInstanceEx().loadProject(projectFile.getPath());

    ProjectManagerEx.getInstanceEx().openTestProject(myProject);
    UIUtil.dispatchAllInvocationEvents(); // startup activities
  }

  public void test() {
    ModuleManager moduleManager = ModuleManager.getInstance(getProject());
    Module[] modules = moduleManager.getModules();
    assertEquals(3, modules.length);

    Module module1 = moduleManager.findModuleByName("test1");
    Module module2 = moduleManager.findModuleByName("test2");
    Module module3 = moduleManager.findModuleByName("test3");
    final GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(module1);
    PsiClass test1 = myJavaFacade.findClass("com.test.TestI", moduleScope);
    PsiClass test2 = myJavaFacade.findClass("com.test.TestI", GlobalSearchScope.moduleScope(module2));
    PsiClass test3 = myJavaFacade.findClass("com.test.TestI", GlobalSearchScope.moduleScope(module3));
    HashSet<PsiElement> expectedImpls1 = new HashSet<>(Arrays.asList(
      myJavaFacade.findClass("com.test.TestIImpl1", moduleScope),
      myJavaFacade.findClass("com.test.TestIImpl2", moduleScope)
    ));
    assertEquals(expectedImpls1, new HashSet<>(getClassImplementations(test1)));

    PsiMethod psiMethod = test1.findMethodsByName("test", false)[0];
    Set<PsiElement> expectedMethodImpl1 = new HashSet<>(Arrays.asList(
      myJavaFacade.findClass("com.test.TestIImpl1", moduleScope).findMethodsByName("test", false)[0],
      myJavaFacade.findClass("com.test.TestIImpl2", moduleScope).findMethodsByName("test", false)[0]
    ));
    CommonProcessors.CollectProcessor<PsiElement> processor = new CommonProcessors.CollectProcessor<>();
    MethodImplementationsSearch.processImplementations(psiMethod, processor, moduleScope);
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
