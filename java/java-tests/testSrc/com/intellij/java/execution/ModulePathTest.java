// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.PathsList;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.io.File;
import java.util.Arrays;

public class ModulePathTest extends BaseConfigurationTestCase {

  public void testModuleInfoInProductionNonModularizedJunit() throws Exception {
    Module module = createEmptyModule();
    JpsMavenRepositoryLibraryDescriptor nonModularizedJupiterDescription =
      new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.3.0");
    JUnitConfiguration configuration = setupConfiguration(nonModularizedJupiterDescription, "prod1", module);
    JavaParameters params4Tests = configuration.getTestObject().createJavaParameters4Tests();
    assertEquals("-ea" +
                 " --patch-module m1=" + CompilerModuleExtension.getInstance(module).getCompilerOutputPathForTests().getPath() +
                 " --add-reads m1=ALL-UNNAMED" +
                 " --add-modules m1 -Didea.test.cyclic.buffer.size=1048576", params4Tests.getVMParametersList().getParametersString());

    //junit is on the classpath
    PathsList classPath = params4Tests.getClassPath();
    Arrays.stream(OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots())
      .map(f -> JarFileSystem.getInstance().getVirtualFileForJar(f).getPath())
      .forEach(path -> assertTrue("path " + path + " is not located on the classpath: " + classPath.getPathsString(),
                                  classPath.getPathList().contains(path)));

    //production module output is on the module path
    PathsList modulePath = params4Tests.getModulePath();
    assertTrue("module path: " + modulePath.getPathsString(),
               modulePath.getPathList().contains(CompilerModuleExtension.getInstance(module).getCompilerOutputPath().getPath()));
  }

  public void _testModuleInfoInProductionModularizedJunit() throws Exception {
    Module module = createEmptyModule();
    JpsMavenRepositoryLibraryDescriptor modularizedJupiterDescription =
      new JpsMavenRepositoryLibraryDescriptor("org.junit.jupiter", "junit-jupiter-api", "5.5.2");
    JUnitConfiguration configuration = setupConfiguration(modularizedJupiterDescription, "prod1", module);
    JavaParameters params4Tests = configuration.getTestObject().createJavaParameters4Tests();
    assertEquals("-ea" +
                 " --patch-module m1=" + CompilerModuleExtension.getInstance(module).getCompilerOutputPathForTests().getPath() +
                 " --add-reads m1=ALL-UNNAMED" +
                 " --add-modules m1 -Didea.test.cyclic.buffer.size=1048576", params4Tests.getVMParametersList().getParametersString());

    //junit is on the classpath
    PathsList classPath = params4Tests.getClassPath();
    Arrays.stream(OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots())
      .map(f -> JarFileSystem.getInstance().getVirtualFileForJar(f).getPath())
      .forEach(path -> assertTrue("path " + path + " is not located on the classpath: " + classPath.getPathsString(),
                                  classPath.getPathList().contains(path)));

    //production module output is on the module path
    PathsList modulePath = params4Tests.getModulePath();
    assertTrue("module path: " + modulePath.getPathsString(),
               modulePath.getPathList().contains(CompilerModuleExtension.getInstance(module).getCompilerOutputPath().getPath()));
  }

  private JUnitConfiguration setupConfiguration(JpsMavenRepositoryLibraryDescriptor libraryDescriptor, String sources, Module module) throws Exception {
    VirtualFile contentRoot = getContentRoot(sources);
    ContentEntry contentEntry = PsiTestUtil.addContentRoot(module, contentRoot);
    contentEntry.addSourceFolder(contentRoot.getUrl() + "/src",false);
    contentEntry.addSourceFolder(contentRoot.getUrl() + "/test",true);
    ModuleRootModificationUtil.updateModel(module, model -> {
      CompilerModuleExtension moduleExtension = model.getModuleExtension(CompilerModuleExtension.class);
      moduleExtension.inheritCompilerOutputPath(false);
      moduleExtension.setCompilerOutputPath(contentRoot.findFileByRelativePath("out/production"));
      moduleExtension.setCompilerOutputPathForTests(contentRoot.findFileByRelativePath("out/test"));
    });
    AbstractTestFrameworkIntegrationTest.addMavenLibs(module, libraryDescriptor);

    Sdk mockJdk = IdeaTestUtil.getMockJdk9();
    ModuleRootModificationUtil.setModuleSdk(module, mockJdk);

    PsiClass aClass = findClass(module, "p.MyTest");
    assertNotNull(aClass);
    assertNotNull(TestFrameworks.detectFramework(aClass));

    return createConfiguration(aClass);
  }

  protected static VirtualFile getContentRoot(String path) {
    String filePath = PathManagerEx.getTestDataPath() + File.separator + "junit" + File.separator + "modulePath" +
                      File.separator + path;
    return LocalFileSystem.getInstance().findFileByPath(filePath.replace(File.separatorChar, '/'));
  }
}
