// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution.configurations;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.roots.ModuleRootManagerTestCase;
import org.intellij.lang.annotations.MagicConstant;

/**
 * @author nik
 */
public class JavaParametersTest extends ModuleRootManagerTestCase {
  public void testLibrary() throws Exception {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());
    assertClasspath(myModule, JavaParameters.JDK_AND_CLASSES_AND_TESTS,
                    getRtJarJdk17(), getJDomJar());
    assertClasspath(myModule, JavaParameters.CLASSES_ONLY,
                    getJDomJar());
    assertClasspath(myModule, JavaParameters.CLASSES_AND_TESTS,
                    getJDomJar());
    assertClasspath(myProject, JavaParameters.JDK_AND_CLASSES_AND_TESTS,
                    getRtJarJdk17(), getJDomJar());
  }

  public void testModuleSourcesAndOutput() throws Exception {
    addSourceRoot(myModule, false);
    addSourceRoot(myModule, true);
    final VirtualFile output = setModuleOutput(myModule, false);
    final VirtualFile testOutput = setModuleOutput(myModule, true);

    assertClasspath(myModule, JavaParameters.CLASSES_ONLY,
                    output);
    assertClasspath(myModule, JavaParameters.CLASSES_AND_TESTS,
                    testOutput, output);
    assertClasspath(myModule, JavaParameters.JDK_AND_CLASSES_AND_TESTS,
                    getRtJarJdk17(), testOutput, output);
  }

  public void testLibraryScope() throws Exception {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary(), DependencyScope.RUNTIME, false);
    ModuleRootModificationUtil.addDependency(myModule, createAsmLibrary(), DependencyScope.TEST, false);

    assertClasspath(myModule, JavaParameters.CLASSES_AND_TESTS,
                    getJDomJar(), getAsmJar());
    assertClasspath(myModule, JavaParameters.CLASSES_ONLY,
                    getJDomJar());
  }

  public void testProvidedScope() throws Exception {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary(), DependencyScope.PROVIDED, false);
    ModuleRootModificationUtil.addDependency(myModule, createAsmLibrary(), DependencyScope.TEST, false);

    assertClasspath(myModule, JavaParameters.CLASSES_AND_TESTS, getJDomJar(), getAsmJar());
    assertClasspath(myModule, JavaParameters.CLASSES_ONLY);
    assertClasspath(myModule, JavaParameters.JDK_AND_CLASSES_AND_PROVIDED, getRtJarJdk17(), getJDomJar());
  }

  public void testModuleDependency() throws Exception {
    final Module dep = createModule("dep");
    final VirtualFile depOutput = setModuleOutput(dep, false);
    final VirtualFile depTestOutput = setModuleOutput(dep, true);
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary());
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.COMPILE, false);

    assertClasspath(myModule, JavaParameters.CLASSES_ONLY,
                    depOutput, getJDomJar());
    assertClasspath(myModule, JavaParameters.CLASSES_AND_TESTS,
                    depTestOutput, depOutput, getJDomJar());
  }

  public void testModuleDependencyScope() throws Exception {
    final Module dep = createModule("dep");
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary());
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.TEST, true);

    assertClasspath(myModule, JavaParameters.CLASSES_ONLY);
    assertClasspath(myModule, JavaParameters.CLASSES_AND_TESTS,
                    getJDomJar());

    assertClasspath(myProject, JavaParameters.CLASSES_ONLY,
                    getJDomJar());
  }

  public void testUseNewestJreVersion() throws CantRunException {
    Module dep = createModule("dep");
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.TEST, true);
    ModuleRootModificationUtil.setModuleSdk(myModule, getMockJdk17WithRtJarOnly());
    ModuleRootModificationUtil.setModuleSdk(dep, getMockJdk18WithRtJarOnly());
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

  /*public void testPreviewLanguageFeatures() throws CantRunException {
    ModuleRootModificationUtil.updateModel(myModule, (model) -> {
      model.getModuleExtension(LanguageLevelModuleExtension.class)
           .setLanguageLevel(LanguageLevel.JDK_11_PREVIEW);
      model.setSdk(IdeaTestUtil.getMockJdk(JavaVersion.compose(11)));
    });
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.configureByModule(myModule, JavaParameters.CLASSES_AND_TESTS);
    assertTrue(javaParameters.getVMParametersList().hasParameter(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY));

    ModuleRootModificationUtil.updateModel(myModule, (model) -> model.getModuleExtension(LanguageLevelModuleExtension.class)
                                                                     .setLanguageLevel(LanguageLevel.JDK_11));
    javaParameters = new JavaParameters();
    javaParameters.configureByModule(myModule, JavaParameters.CLASSES_AND_TESTS);
    assertFalse(javaParameters.getVMParametersList().hasParameter(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY));
  }*/
}
