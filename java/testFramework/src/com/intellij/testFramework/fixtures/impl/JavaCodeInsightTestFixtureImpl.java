/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testFramework.fixtures.impl;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

/**
 * @author yole
 */
@SuppressWarnings("TestOnlyProblems")
public class JavaCodeInsightTestFixtureImpl extends CodeInsightTestFixtureImpl implements JavaCodeInsightTestFixture {
  public JavaCodeInsightTestFixtureImpl(IdeaProjectTestFixture projectFixture, TempDirTestFixture tempDirFixture) {
    super(projectFixture, tempDirFixture);
  }

  @Override
  public JavaPsiFacadeEx getJavaFacade() {
    assertInitialized();
    return JavaPsiFacadeEx.getInstanceEx(getProject());
  }

  @Override
  public PsiClass addClass(@NotNull @NonNls final String classText) {
    assertInitialized();

    String rootPath = getTempDirPath();

    // Make sure rootPath belongs to the module:
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule());
    if (rootManager != null) {
      VirtualFile[] allSourceRoots = rootManager.getSourceRoots(true);
      VirtualFile[] productionSourceRoots = rootManager.getSourceRoots(false);
      VirtualFile rootVirtualFile = getTempDirFixture().getFile(""); // should be equivalent to rootPath.
      if (!ArrayUtil.contains(rootVirtualFile, allSourceRoots) && !ArrayUtil.isEmpty(productionSourceRoots)) {
        // The temp directory is not a source root, so there's little point adding the class there. Pick a production source root instead.
        rootPath = productionSourceRoots[0].getPath();
      }
    }

    final PsiClass psiClass = addClass(rootPath, classText);
    final VirtualFile file = psiClass.getContainingFile().getVirtualFile();
    allowTreeAccessForFile(file);
    return psiClass;
  }

  private PsiClass addClass(@NonNls final String rootPath, @NotNull @NonNls final String classText) {
    final String qName =
      ReadAction.compute(() -> {
        final PsiFileFactory factory = PsiFileFactory.getInstance(getProject());
        final PsiJavaFile javaFile = (PsiJavaFile)factory.createFileFromText("a.java", JavaFileType.INSTANCE, classText);
        return javaFile.getClasses()[0].getQualifiedName();
      });
    assert qName != null;
    final PsiFile psiFile = addFileToProject(rootPath, qName.replace('.', '/') + ".java", classText);
    return ReadAction.compute(() -> ((PsiJavaFile)psiFile).getClasses()[0]);
  }

  @Override
  @NotNull
  public PsiClass findClass(@NotNull @NonNls final String name) {
    PsiClass aClass = getJavaFacade().findClass(name, GlobalSearchScope.allScope(getProject()));
    Assert.assertNotNull("Class " + name + " not found", aClass);
    return aClass;
  }

  @Override
  @NotNull
  public PsiPackage findPackage(@NotNull @NonNls final String name) {
    final PsiPackage aPackage = getJavaFacade().findPackage(name);
    Assert.assertNotNull("Package " + name + " not found", aPackage);
    return aPackage;
  }

}
