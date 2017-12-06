/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
}
