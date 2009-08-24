package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
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
}

