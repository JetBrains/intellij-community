/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author yole
 */
public class JavaCodeInsightTestFixtureImpl extends CodeInsightTestFixtureImpl implements JavaCodeInsightTestFixture {
  public JavaCodeInsightTestFixtureImpl(IdeaProjectTestFixture projectFixture, TempDirTestFixture tempDirFixture) {
    super(projectFixture, tempDirFixture);
  }

  public JavaPsiFacade getJavaFacade() {
    assertInitialized();
    return JavaPsiFacade.getInstance(getProject());
  }

  public PsiClass addClass(@NotNull @NonNls final String classText) throws IOException {
    assertInitialized();
    final PsiClass psiClass = addClass(getTempDirPath(), classText);
    final VirtualFile file = psiClass.getContainingFile().getVirtualFile();
    allowTreeAccessForFile(file);
    return psiClass;
  }

  private PsiClass addClass(@NonNls String rootPath, @NotNull @NonNls final String classText) throws IOException {
    final PsiClass aClass = ((PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("a.java", classText)).getClasses()[0];
    final String qName = aClass.getQualifiedName();
    assert qName != null;

    final PsiFile psiFile = addFileToProject(rootPath, qName.replace('.', '/') + ".java", classText);
    return ((PsiJavaFile)psiFile).getClasses()[0];
  }

  @NotNull
  public PsiClass findClass(@NotNull @NonNls final String name) {
    final PsiClass aClass = getJavaFacade().findClass(name, ProjectScope.getProjectScope(getProject()));
    assertNotNull("Class " + name + " not found", aClass);
    return aClass;
  }
}
