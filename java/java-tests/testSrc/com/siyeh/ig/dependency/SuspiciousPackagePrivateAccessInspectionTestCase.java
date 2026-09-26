// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.dependency;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.io.IOException;

public abstract class SuspiciousPackagePrivateAccessInspectionTestCase extends LightJavaInspectionTestCase {
  private final ProjectWithDepModuleDescriptor myProjectDescriptor = new ProjectWithDepModuleDescriptor(LanguageLevel.HIGHEST);
  private final String myExtension;

  public SuspiciousPackagePrivateAccessInspectionTestCase(String extension) {
    myExtension = extension;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.copyDirectoryToProject("dep", ProjectWithDepModuleDescriptor.getDepModuleSourceRoot());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myProjectDescriptor.cleanUpSources();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void doTestWithDependency() {
    myFixture.configureByFile("src/" + getTestName(false) + "." + myExtension);
    myFixture.testHighlighting(true, false, false);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return myProjectDescriptor;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SuspiciousPackagePrivateAccessInspection();
  }

  private static class ProjectWithDepModuleDescriptor extends ProjectDescriptor {
    private static final String DEP_MODULE_SOURCE_ROOT = "dep-module-src";
    private VirtualFile mySourceRoot;
    private VirtualFile myTestSourceRoot;

    ProjectWithDepModuleDescriptor(@NotNull LanguageLevel languageLevel) {
      super(languageLevel);
    }

    @Override
    public void setUpProject(@NotNull Project project, @NotNull SetupHandler handler) throws Exception {
      super.setUpProject(project, handler);
      WriteAction.run(() -> {
        Module mainModule = ModuleManager.getInstance(project).findModuleByName(TEST_MODULE_NAME);
        File depModuleDir = FileUtil.createTempDirectory("dep-module-", null);
        Module depModule = createModule(project, depModuleDir + "/dep.iml");
        ModuleRootModificationUtil.updateModel(depModule, model -> {
          model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(myLanguageLevel);
          model.setSdk(getSdk());
          mySourceRoot = createSourceRoot(depModule, DEP_MODULE_SOURCE_ROOT);
          model.addContentEntry(mySourceRoot).addSourceFolder(mySourceRoot, JavaSourceRootType.SOURCE);
          myTestSourceRoot = createSourceRoot(depModule, "depTests");
          model.addContentEntry(myTestSourceRoot).addSourceFolder(myTestSourceRoot, JavaSourceRootType.TEST_SOURCE);
        });
        ModuleRootModificationUtil.addDependency(mainModule, depModule);
      });
    }

    public void cleanUpSources() throws IOException {
      if (mySourceRoot != null) {
        WriteAction.run(() -> mySourceRoot.delete(this));
      }
      if (myTestSourceRoot != null) {
        WriteAction.run(() -> myTestSourceRoot.delete(this));
      }
    }

    @NotNull
    private static String getDepModuleSourceRoot() {
      return "../" + DEP_MODULE_SOURCE_ROOT;
    }
  }
}
