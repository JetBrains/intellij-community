// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataProvider;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class AbstractApplyAndRevertTestCase extends HeavyPlatformTestCase {
  protected CompilerTester myCompilerTester;
  protected Project myProject;

  private String oldMacroValue;

  @Override
  public Object getData(@NotNull String dataId) {
    return myProject == null ? null : new TestDataProvider(myProject).getData(dataId);
  }

  private Generator<VirtualFile> javaFiles() {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
    List<VirtualFile> allFiles = new ArrayList<>(FilenameIndex.getAllFilesByExt(myProject, "java", projectScope));
    if (allFiles.isEmpty()) {
      throw new IllegalStateException("No java files in project???");
    }
    ContainerUtil.sort(allFiles, Comparator.comparing(VirtualFile::getPath));
    return Generator.sampledFrom(allFiles);
  }

  protected Generator<PsiJavaFile> psiJavaFiles() {
    return javaFiles().map(vf -> (PsiJavaFile)PsiManager.getInstance(myProject).findFile(vf));
  }

  protected abstract String getTestDataPath();

  @Override
  public void setUp() throws Exception {
    super.setUp();

    PathMacros pathMacros = PathMacros.getInstance();
    oldMacroValue = pathMacros.getValue(PathMacrosImpl.MAVEN_REPOSITORY);
    pathMacros.setMacro(PathMacrosImpl.MAVEN_REPOSITORY, getDefaultMavenRepositoryPath());

    myProject = ProjectUtil.openOrImport(Paths.get(getTestDataPath()).normalize());

    InspectionProfileImpl.INIT_INSPECTIONS = true;

    DefaultLogger.disableStderrDumping(getTestRootDisposable());
  }

  protected final void initCompiler() {
    try {
      Module module = ModuleManager.getInstance(myProject).getModules()[0];
      myCompilerTester = new CompilerTester(module);
    }
    catch (Throwable e) {
      ExceptionUtil.rethrowAllAsUnchecked(e);
    }
  }

  protected String getDefaultMavenRepositoryPath() {
    final String root = System.getProperty("user.home", null);
    return (root != null ? new File(root, ".m2/repository") : new File(".m2/repository")).getAbsolutePath();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      if (myCompilerTester != null) {
        myCompilerTester.tearDown();
      }
      PathMacros.getInstance().setMacro(PathMacrosImpl.MAVEN_REPOSITORY, oldMacroValue);
      Project project = myProject;
      myProject = null;
      PlatformTestUtil.forceCloseProjectWithoutSaving(project);
      InspectionProfileImpl.INIT_INSPECTIONS = false;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected static void checkCompiles(List<CompilerMessage> messages) {
    List<CompilerMessage> compilerMessages = filterErrors(messages);
    if (!compilerMessages.isEmpty()) {
      fail(StringUtil.join(compilerMessages, mes -> mes.getMessage(), "\n"));
    }
  }

  protected static List<CompilerMessage> filterErrors(List<CompilerMessage> messages) {
    return ContainerUtil.filter(messages, message -> message.getCategory() == CompilerMessageCategory.ERROR);
  }
}
