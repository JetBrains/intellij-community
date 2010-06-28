/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectClasspathTraversing;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;

/**
 * @author nik
 */
@SuppressWarnings({"deprecation"})
public class ProjectClasspathTraversingTest extends ModuleRootManagerTestCase {
  public void testLibrary() throws Exception {
    addLibraryDependency(myModule, createJDomLibrary());

    doTest(ProjectClasspathTraversing.FULL_CLASSPATH, getRtJar(), getJDomJar());
    doTest(ProjectClasspathTraversing.FULL_CLASS_RECURSIVE_WO_JDK, getJDomJar());
    doTest(ProjectClasspathTraversing.FULL_CLASSPATH_RECURSIVE, getRtJar(), getJDomJar());
    doTest(ProjectClasspathTraversing.FULL_CLASSPATH_WITHOUT_JDK_AND_TESTS, getJDomJar());
    doTest(ProjectClasspathTraversing.FULL_CLASSPATH_WITHOUT_TESTS, getRtJar(), getJDomJar());
  }
  
  public void testModuleOutput() throws Exception {
    addSourceRoot(myModule, false);
    final VirtualFile output = setModuleOutput(myModule, false);
    final VirtualFile testOutput = setModuleOutput(myModule, true);

    doTest(ProjectClasspathTraversing.FULL_CLASSPATH, getRtJar(), testOutput, output);
    doTest(ProjectClasspathTraversing.FULL_CLASS_RECURSIVE_WO_JDK, testOutput, output);
    doTest(ProjectClasspathTraversing.FULL_CLASSPATH_RECURSIVE, getRtJar(), testOutput, output);
    doTest(ProjectClasspathTraversing.FULL_CLASSPATH_WITHOUT_JDK_AND_TESTS, output);
    doTest(ProjectClasspathTraversing.FULL_CLASSPATH_WITHOUT_TESTS, getRtJar(), output);
  }

  public void testLibraryScope() throws Exception {
    addLibraryDependency(myModule, createJDomLibrary(), DependencyScope.TEST, true);

    doTest(ProjectClasspathTraversing.FULL_CLASSPATH, getRtJar(), getJDomJar());
    doTest(ProjectClasspathTraversing.FULL_CLASS_RECURSIVE_WO_JDK, getJDomJar());
    doTest(ProjectClasspathTraversing.FULL_CLASSPATH_RECURSIVE, getRtJar(), getJDomJar());
    doTest(ProjectClasspathTraversing.FULL_CLASSPATH_WITHOUT_JDK_AND_TESTS);
    doTest(ProjectClasspathTraversing.FULL_CLASSPATH_WITHOUT_TESTS, getRtJar());
  }

  public void testModuleDependency() throws Exception {
    final Module dep = createModule("dep");
    final VirtualFile output = setModuleOutput(dep, false);
    final VirtualFile testOutput = setModuleOutput(dep, true);
    addLibraryDependency(dep, createJDomLibrary());
    addModuleDependency(myModule, dep);

    doTest(ProjectClasspathTraversing.FULL_CLASSPATH, getRtJar());
    doTest(ProjectClasspathTraversing.FULL_CLASS_RECURSIVE_WO_JDK, testOutput, output, getJDomJar());
    doTest(ProjectClasspathTraversing.FULL_CLASSPATH_RECURSIVE, getRtJar(), testOutput, output, getJDomJar());
    doTest(ProjectClasspathTraversing.FULL_CLASSPATH_WITHOUT_JDK_AND_TESTS, output, getJDomJar());
    doTest(ProjectClasspathTraversing.FULL_CLASSPATH_WITHOUT_TESTS, getRtJar(), output, getJDomJar());
  }

  private void doTest(ProjectRootsTraversing.RootTraversePolicy policy, VirtualFile... roots) {
    assertRoots(ProjectRootsTraversing.collectRoots(myModule, policy), roots);
    assertRoots(collectByOrderEnumerator(policy), roots);
  }

  private PathsList collectByOrderEnumerator(ProjectRootsTraversing.RootTraversePolicy policy) {
    if (policy == ProjectClasspathTraversing.FULL_CLASSPATH) {
      return OrderEnumerator.orderEntries(myModule).withoutDepModules().getPathsList();
    }
    if (policy == ProjectClasspathTraversing.FULL_CLASS_RECURSIVE_WO_JDK) {
      return OrderEnumerator.orderEntries(myModule).withoutSdk().recursively().getPathsList();
    }
    if (policy == ProjectClasspathTraversing.FULL_CLASSPATH_RECURSIVE) {
      return OrderEnumerator.orderEntries(myModule).recursively().getPathsList();
    }
    if (policy == ProjectClasspathTraversing.FULL_CLASSPATH_WITHOUT_JDK_AND_TESTS) {
      return OrderEnumerator.orderEntries(myModule).withoutSdk().productionOnly().recursively().getPathsList();
    }
    if (policy == ProjectClasspathTraversing.FULL_CLASSPATH_WITHOUT_TESTS) {
      return OrderEnumerator.orderEntries(myModule).productionOnly().recursively().getPathsList();
    }
    throw new AssertionError(policy);
  }
}
