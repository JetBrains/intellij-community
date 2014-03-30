/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;

import java.io.File;

/**
 *  @author dsl
 */
public class SCR17094Test extends PsiTestCase {
  protected void setUpClasses(final String s) throws Exception {
    final String testRoot = PathManagerEx.getTestDataPath() + "/psi/repositoryUse/scr17094";
    VirtualFile classesRoot = WriteCommandAction.runWriteCommandAction(null, new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        String path = testRoot + "/" + s;
        path = path.replace(File.separatorChar, '/');
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      }
    });
    assertNotNull(classesRoot);
    ModuleRootModificationUtil.addModuleLibrary(myModule, classesRoot.getUrl());
  }

  @Override
  protected void setUpJdk() {

  }

  public void testSRC() throws Exception {
    setUpClasses("classes");
    final JavaPsiFacade psiManager = getJavaFacade();
    final PsiClass classA = psiManager.findClass("a.a.a.a.e.f.i", GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(classA);
  }

  public void test3() throws Exception {
    setUpClasses("classes2");
    final JavaPsiFacade psiManager = getJavaFacade();
    final PsiClass classA = psiManager.findClass("com.intellij.internal.f.a.b.a.i", GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(classA);
  }
}
