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
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;
import org.jetbrains.jps.util.JpsPathUtil;

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
    rebuildAll();
  }

  public void testTestOnProductionDependency() {
    String depRoot = PathUtil.getParentPath(createFile("dep/A.java", "class A{}"));
    String testRoot = PathUtil.getParentPath(createFile("test/B.java", "class B extends A{}"));
    JpsModule main = addModule("main");
    main.addSourceRoot(JpsPathUtil.pathToUrl(testRoot), JavaSourceRootType.TEST_SOURCE);
    JpsModule dep = addModule("dep", depRoot);
    main.getDependenciesList().addModuleDependency(dep);
    rebuildAll();
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
    rebuildAll();
  }

  private static void addExportedDependency(JpsModule main, JpsModule dep) {
    JpsModuleDependency dependency = main.getDependenciesList().addModuleDependency(dep);
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).setExported(true);
  }
}
