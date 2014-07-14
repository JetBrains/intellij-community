/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.util.ui.UIUtil;
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
    final PsiClass psiClass = addClass(getTempDirPath(), classText);
    final VirtualFile file = psiClass.getContainingFile().getVirtualFile();
    allowTreeAccessForFile(file);
    return psiClass;
  }

  private PsiClass addClass(@NonNls final String rootPath, @NotNull @NonNls final String classText) {
    final String qName =
      ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          final PsiFileFactory factory = PsiFileFactory.getInstance(getProject());
          final PsiJavaFile javaFile = (PsiJavaFile)factory.createFileFromText("a.java", JavaFileType.INSTANCE, classText);
          return javaFile.getClasses()[0].getQualifiedName();
        }
      });
    assert qName != null;
    final PsiFile psiFile = addFileToProject(rootPath, qName.replace('.', '/') + ".java", classText);
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
            public PsiClass compute() {
              return ((PsiJavaFile)psiFile).getClasses()[0];
            }
          });
  }

  @Override
  @NotNull
  public PsiClass findClass(@NotNull @NonNls final String name) {
    final PsiClass aClass = getJavaFacade().findClass(name, ProjectScope.getProjectScope(getProject()));
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

  @Override
  public void tearDown() throws Exception {
    try {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          ((PsiModificationTrackerImpl)getPsiManager().getModificationTracker()).incCounter();// drop all caches
        }
      });
    }
    finally {
      super.tearDown();
    }
  }
}
