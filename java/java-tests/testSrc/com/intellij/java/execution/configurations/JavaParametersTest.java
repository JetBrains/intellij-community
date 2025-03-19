// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.configurations;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.roots.ModuleRootManagerTestCase;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.lang.JavaVersion;
import org.intellij.lang.annotations.MagicConstant;

public class JavaParametersTest extends ModuleRootManagerTestCase {
  public void testLibrary() throws Exception {
    ModuleRootModificationUtil.addDependency(myModule, createFastUtilLibrary());
    assertClasspath(myModule, JavaParameters.JDK_AND_CLASSES_AND_TESTS, getRtJarJdk17(), getFastUtilJar());
    assertClasspath(myModule, JavaParameters.CLASSES_ONLY, getFastUtilJar());
    assertClasspath(myModule, JavaParameters.CLASSES_AND_TESTS, getFastUtilJar());
    assertClasspath(myProject, JavaParameters.JDK_AND_CLASSES_AND_TESTS, getRtJarJdk17(), getFastUtilJar());
  }

  private Library createFastUtilLibrary() {
    return createLibrary("fastutil-min", getFastUtilJar(), IntelliJProjectConfiguration.getJarFromSingleJarProjectLibrary("gson"));
  }

  public void testModuleSourcesAndOutput() throws Exception {
    addSourceRoot(myModule, false);
    addSourceRoot(myModule, true);
    VirtualFile output = setModuleOutput(myModule, false);
    VirtualFile testOutput = setModuleOutput(myModule, true);

    assertClasspath(myModule, JavaParameters.CLASSES_ONLY, output);
    assertClasspath(myModule, JavaParameters.CLASSES_AND_TESTS, testOutput, output);
    assertClasspath(myModule, JavaParameters.JDK_AND_CLASSES_AND_TESTS, getRtJarJdk17(), testOutput, output);
  }

  public void testLibraryScope() throws Exception {
    ModuleRootModificationUtil.addDependency(myModule, createFastUtilLibrary(), DependencyScope.RUNTIME, false);
    ModuleRootModificationUtil.addDependency(myModule, createAsmLibrary(), DependencyScope.TEST, false);

    assertClasspath(myModule, JavaParameters.CLASSES_AND_TESTS, getFastUtilJar(), getAsmJar());
    assertClasspath(myModule, JavaParameters.CLASSES_ONLY, getFastUtilJar());
  }

  public void testProvidedScope() throws Exception {
    ModuleRootModificationUtil.addDependency(myModule, createFastUtilLibrary(), DependencyScope.PROVIDED, false);
    ModuleRootModificationUtil.addDependency(myModule, createAsmLibrary(), DependencyScope.TEST, false);

    assertClasspath(myModule, JavaParameters.CLASSES_AND_TESTS, getFastUtilJar(), getAsmJar());
    assertClasspath(myModule, JavaParameters.CLASSES_ONLY);
    assertClasspath(myModule, JavaParameters.JDK_AND_CLASSES_AND_PROVIDED, getRtJarJdk17(), getFastUtilJar());
  }

  public void testModuleDependency() throws Exception {
    final Module dep = createModule("dep");
    final VirtualFile depOutput = setModuleOutput(dep, false);
    final VirtualFile depTestOutput = setModuleOutput(dep, true);
    ModuleRootModificationUtil.addDependency(dep, createFastUtilLibrary());
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.COMPILE, false);

    assertClasspath(myModule, JavaParameters.CLASSES_ONLY,
                    depOutput, getFastUtilJar());
    assertClasspath(myModule, JavaParameters.CLASSES_AND_TESTS,
                    depTestOutput, depOutput, getFastUtilJar());
  }

  public void testModuleDependencyScope() throws Exception {
    final Module dep = createModule("dep");
    ModuleRootModificationUtil.addDependency(dep, createFastUtilLibrary());
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.TEST, true);

    assertClasspath(myModule, JavaParameters.CLASSES_ONLY);
    assertClasspath(myModule, JavaParameters.CLASSES_AND_TESTS,
                    getFastUtilJar());

    assertClasspath(myProject, JavaParameters.CLASSES_ONLY,
                    getFastUtilJar());
  }

  public void testUseNewestJreVersion() throws CantRunException {
    Module dep = createModule("dep");
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.TEST, true);
    ModuleRootModificationUtil.setModuleSdk(myModule, getMockJdk17WithRtJarOnly());
    Sdk sdk = getMockJdk18WithRtJarOnly();
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(sdk, getTestRootDisposable()));
    ModuleRootModificationUtil.setModuleSdk(dep, sdk);
    assertClasspath(myModule, JavaParameters.JDK_AND_CLASSES, getRtJarJdk17());
    assertClasspath(myModule, JavaParameters.JDK_AND_CLASSES_AND_TESTS, getRtJarJdk18());
  }

  private static void assertClasspath(final Module module, @MagicConstant(flagsFromClass = JavaParameters.class) int type, VirtualFile... roots) throws CantRunException {
    final JavaParameters javaParameters = new JavaParameters();
    javaParameters.configureByModule(module, type);
    assertRoots(javaParameters.getClassPath(), roots);
  }

  private void assertClasspath(final Project project, @MagicConstant(flagsFromClass = JavaParameters.class) int type, VirtualFile... roots) throws CantRunException {
    final JavaParameters javaParameters = new JavaParameters();
    javaParameters.configureByProject(project, type, getTestProjectJdk());
    assertRoots(javaParameters.getClassPath(), roots);
  }

  public void testPreviewLanguageFeatures() throws CantRunException {
    ModuleRootModificationUtil.updateModel(myModule, (model) -> {
      model.getModuleExtension(LanguageLevelModuleExtension.class)
           .setLanguageLevel(LanguageLevel.JDK_21_PREVIEW);
      Sdk mockJdk = IdeaTestUtil.getMockJdk(JavaVersion.compose(14));
      WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(mockJdk, myProject));
      model.setSdk(mockJdk);
    });
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.configureByModule(myModule, JavaParameters.CLASSES_AND_TESTS);
    assertTrue(javaParameters.getVMParametersList().hasParameter(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY));

    ModuleRootModificationUtil.updateModel(myModule, (model) -> model.getModuleExtension(LanguageLevelModuleExtension.class)
                                                                     .setLanguageLevel(LanguageLevel.JDK_15));
    javaParameters = new JavaParameters();
    javaParameters.configureByModule(myModule, JavaParameters.CLASSES_AND_TESTS);
    assertFalse(javaParameters.getVMParametersList().hasParameter(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY));
  }
}
