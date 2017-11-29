/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java;

import com.intellij.util.PathUtil;
import org.jetbrains.jps.builders.BuildTargetIndex;
import org.jetbrains.jps.builders.CompileScopeTestBuilder;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.CompileContextImpl;
import org.jetbrains.jps.incremental.CompileScope;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.List;

/**
 * @author nik
 */
public class DependentModulesCompilationTest extends JpsBuildTestCase {
  public void testSimpleDependency() {
    String depRoot = PathUtil.getParentPath(createFile("dep/A.java", "class A{}"));
    String mainRoot = PathUtil.getParentPath(createFile("main/B.java", "class B extends A{}"));
    JpsModule main = addModule("main", mainRoot);
    JpsModule dep = addModule("dep", depRoot);
    main.getDependenciesList().addModuleDependency(dep);
    rebuildAllModules();
  }

  public void testTestOnProductionDependency() {
    String depRoot = PathUtil.getParentPath(createFile("dep/A.java", "class A{}"));
    String testRoot = PathUtil.getParentPath(createFile("test/B.java", "class B extends A{}"));
    JpsModule main = addModule("main");
    main.addSourceRoot(JpsPathUtil.pathToUrl(testRoot), JavaSourceRootType.TEST_SOURCE);
    JpsModule dep = addModule("dep", depRoot);
    main.getDependenciesList().addModuleDependency(dep);
    rebuildAllModules();
  }

  public void testTransitiveDependencyViaDummyModule() {
    String depRoot = PathUtil.getParentPath(createFile("dep/A.java", "class A{}"));
    String mainRoot = PathUtil.getParentPath(createFile("main/B.java", "class B extends A{}"));
    JpsModule main = addModule("main", mainRoot);
    JpsModule dep = addModule("dep", depRoot);
    JpsModule dummy = addModule("dummy");
    JpsModule dummy2 = addModule("dummy2");
    dep.getDependenciesList().addModuleDependency(main);//force 'main' to be built before 'dep' unless transitive dependency is correctly considered
    main.getDependenciesList().addModuleDependency(dummy);
    addExportedDependency(dummy, dummy2);
    addExportedDependency(dummy2, dep);
    dummy2.getDependenciesList().addModuleDependency(dummy);

    //if dummy targets are completely ignored, compilation of 'main' module will fail because 'dep' module won't be compiled
    rebuildAllModules();
  }

  //https://youtrack.jetbrains.com/issue/IDEA-129728
  public void testIgnoreDependenciesFromDummyTargets() {
    String mSrcRoot = PathUtil.getParentPath(createFile("m/src/A.java", "class A{}"));
    String mTestRoot = PathUtil.getParentPath(createFile("m/test/Test.java", "class Test{}"));
    String tSrcRoot = PathUtil.getParentPath(createFile("t/T.java", "class T{}"));
    JpsModule m = addModule("m", mSrcRoot);
    m.addSourceRoot(JpsPathUtil.pathToUrl(mTestRoot), JavaSourceRootType.TEST_SOURCE);
    JpsModule t = addModule("t", tSrcRoot);
    JpsModuleRootModificationUtil.addDependency(t, m);
    JpsModuleRootModificationUtil.addDependency(m, t, JpsJavaDependencyScope.TEST, false);

    CompileScope scope = CompileScopeTestBuilder.rebuild().allModules().build();
    ProjectDescriptor descriptor = createProjectDescriptor(BuildLoggingManager.DEFAULT);
    try {
      BuildTargetIndex targetIndex = descriptor.getBuildTargetIndex();
      CompileContext context = CompileContextImpl.createContextForTests(scope, descriptor);
      List<BuildTargetChunk> chunks = targetIndex.getSortedTargetChunks(context);
      for (BuildTargetChunk chunk : chunks) {
        assertTrue("Circular dependency between build targets " + chunk.getTargets(), chunk.getTargets().size() == 1);
      }
      assertEmpty(targetIndex.getDependencies(new ModuleBuildTarget(t, JavaModuleBuildTargetType.TEST), context));
    }
    finally {
      descriptor.release();
    }
  }

  public void testCleanOutputForDeletedModuleTarget() {
    String srcRoot = PathUtil.getParentPath(createFile("main/A.java", "class A{}"));
    JpsModule main = addModule("main", srcRoot);
    rebuildAllModules();
    main.removeSourceRoot(JpsPathUtil.pathToUrl(srcRoot), JavaSourceRootType.SOURCE);
    doBuild(CompileScopeTestBuilder.make().module(main)).assertSuccessful();
    assertDoesntExist(new File(getOrCreateProjectDir(), getModuleOutputRelativePath(main) + "/A.class"));
  }

  private static void addExportedDependency(JpsModule main, JpsModule dep) {
    JpsModuleDependency dependency = main.getDependenciesList().addModuleDependency(dep);
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).setExported(true);
  }
}
