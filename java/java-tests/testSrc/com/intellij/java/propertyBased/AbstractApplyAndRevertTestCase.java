// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.application.UtilKt;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.TestDataProvider;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.jetCheck.Generator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractApplyAndRevertTestCase extends PlatformTestCase {
  protected CompilerTester myCompilerTester;
  protected Project myProject;

  @Override
  public Object getData(String dataId) {
    return myProject == null ? null : new TestDataProvider(myProject).getData(dataId);
  }

  private Generator<VirtualFile> javaFiles() {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
    List<VirtualFile> allFiles = new ArrayList<>(FilenameIndex.getAllFilesByExt(myProject, "java", projectScope));
    return Generator.sampledFrom(allFiles);
  }

  protected Generator<PsiJavaFile> psiJavaFiles() {
    return javaFiles().map(vf -> (PsiJavaFile)PsiManager.getInstance(myProject).findFile(vf));
  }

  @Override
  protected boolean shouldRunTest() {
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      return false;
    }
    return super.shouldRunTest();
  }

  protected abstract String getTestDataPath();

  public void setUp() throws Exception {
    super.setUp();
    PathMacros.getInstance().setMacro("MAVEN_REPOSITORY", getDefaultMavenRepositoryPath());
    WriteAction.run(() -> ProjectJdkTable.getInstance().addJdk(JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk(), getTestRootDisposable()));
    UtilKt.runInAllowSaveMode(() -> {
      ApplicationManager.getApplication().saveAll();
      return Unit.INSTANCE;
    });

    myProject = ProjectUtil.openOrImport(getTestDataPath(), null, false);

    WriteAction.run(
      () -> ProjectRootManager.getInstance(myProject).setProjectSdk(JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk()));

    InspectionProfileImpl.INIT_INSPECTIONS = true;

    DefaultLogger.disableStderrDumping(getTestRootDisposable());
  }

  protected void initCompiler() {
    try {
      myCompilerTester = new CompilerTester(myProject, ContainerUtil.list(ModuleManager.getInstance(myProject).getModules()[0]));
    }
    catch (Throwable e) {
      fail(e.getMessage());
    }
  }

  protected String getDefaultMavenRepositoryPath() {
    final String root = System.getProperty("user.home", null);
    return (root != null ? new File(root, ".m2/repository") : new File(".m2/repository")).getAbsolutePath();
  }

  public void tearDown() throws Exception {
    try {
      if (myCompilerTester != null) {
        myCompilerTester.tearDown();
      }
   
      ProjectManager.getInstance().closeProject(myProject);
      WriteAction.run(() -> Disposer.dispose(myProject));
      
      myProject = null;
      InspectionProfileImpl.INIT_INSPECTIONS = false;
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
    return messages.stream()
      .filter(message -> message.getCategory() == CompilerMessageCategory.ERROR)
      .collect(Collectors.toList());
  }
}
