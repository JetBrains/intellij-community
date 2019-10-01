// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ether;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.BuildResult;
import org.jetbrains.jps.builders.CompileScopeTestBuilder;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.java.ModulePathSplitter;
import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public class Java9Test extends IncrementalTestCase {

  public Java9Test() {
    super("java9-features");
  }

  @Override
  protected boolean shouldRunTest() {
    if (!SystemInfo.IS_AT_LEAST_JAVA9) {
      System.out.println("Test '" + getTestName(false) + "' skipped because it requires at least java 9 runtime");
      return false;
    }
    return super.shouldRunTest();
  }

  @Override
  protected BuildResult doTestBuild(int makesCount) {
    setupModules();
    return super.doTestBuild(makesCount);
  }

  public void testModuleInfoAdded() {
    // expected result: the whole target is recompiled after the module-info.java file was newly added
    // because necessary 'require' directives may be missing from the newly added module-info file
    final BuildResult buildResult = doTest();
    buildResult.assertSuccessful();
  }

  public void testRemoveModuleRequires() {
    final BuildResult buildResult = doTest();
    buildResult.assertFailed();
  }

  public void testRemoveTransitiveModuleRequires() {
    final BuildResult buildResult = doTest();
    buildResult.assertFailed();
  }

  public void testChangeTransitiveModuleRequires() {
    final BuildResult buildResult = doTest();
    buildResult.assertFailed();
  }

  public void testChangeQualifiedTransitiveModuleRequires() {
    final BuildResult buildResult = doTest();
    buildResult.assertSuccessful();
  }

  public void testRemoveModuleExports() {
    final BuildResult buildResult = doTest();
    buildResult.assertSuccessful();
  }

  public void testRemoveTransitiveModuleExports() {
    final BuildResult buildResult = doTest();
    buildResult.assertFailed();
  }

  public void testRemoveQualifiedModuleExports() {
    final BuildResult buildResult = doTest();
    buildResult.assertSuccessful();
  }

  public void testRemoveQualifiedTransitiveModuleExports() {
    final BuildResult buildResult = doTest();
    buildResult.assertFailed();
  }

  public void testChangeQualifiedTransitiveModuleExportsNoRebuild() {
    final BuildResult buildResult = doTest();
    buildResult.assertSuccessful();
  }

  public void testChangeQualifiedTransitiveModuleExportsRebuildIndirectDeps() {
    final BuildResult buildResult = doTest();
    buildResult.assertFailed();
  }

  public void testChangeQualifiedTransitiveModuleExportsRebuildDirectDeps() {
    final BuildResult buildResult = doTest();
    buildResult.assertSuccessful();
  }

  public void testIntegrateAfterErrors() {
    setupInitialProject();
    setupModules();

    doBuild(CompileScopeTestBuilder.rebuild().allModules()).assertFailed();
    modify(0);
    doBuild(CompileScopeTestBuilder.make().allModules()).assertSuccessful();
    modify(1);
    doBuild(CompileScopeTestBuilder.make().allModules()).assertFailed();
  }

  public void testSplitModulePath() {
    setupInitialProject();
    final Map<String, JpsModule> modules = setupModules();

    assertEquals(1, modules.size());

    final JpsModule module = modules.values().iterator().next();
    final ModuleBuildTarget target = new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION);

    final File outputDir = target.getOutputDir();
    final File libDir = new File(getAbsolutePath("lib"));
    for (File jarFile : libDir.listFiles((dir, name) -> name.endsWith(".jar"))) {
      JpsLibrary lib = addLibrary(jarFile.getName(), jarFile);
      JpsModuleRootModificationUtil.addDependency(module, lib, JpsJavaDependencyScope.COMPILE, false);
    }

    final File moduleInfoPath = new File(getAbsolutePath("moduleA/src/module-info.java"));

    final ModulePathSplitter splitter = new ModulePathSplitter();
    final Collection<File> dependencies = ProjectPaths.getCompilationModulePath(new ModuleChunk(Collections.singleton(target)), false);
    final Pair<Collection<File>, Collection<File>> split = splitter.splitPath(moduleInfoPath, Collections.singleton(outputDir), dependencies);
    final Collection<File> modulePath = split.first;
    final Collection<File> classpath = split.second;

    assertEquals(4, modulePath.size());
    assertTrue(modulePath.contains(outputDir));
    assertTrue(modulePath.contains(new File(libDir, "module_lib_1.jar")));
    assertTrue(modulePath.contains(new File(libDir, "module_lib_2.jar")));
    assertTrue(modulePath.contains(new File(libDir, "module_lib_util.jar")));

    assertEquals(1, classpath.size());
    assertTrue(classpath.contains(new File(libDir, "module_lib_3.jar")));
  }
}
