package com.intellij.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;

/**
 * @author nik
 */
@SuppressWarnings({"deprecation"})
public class ProjectRootsTraversingTest extends ModuleRootManagerTestCase {

  public void testLibrary() throws Exception {
    addLibraryDependency(myModule, createJDomLibrary());
    doTest(ProjectRootsTraversing.LIBRARIES_AND_JDK, getRtJar(), getJDomJar());
    doTest(ProjectRootsTraversing.PROJECT_LIBRARIES, getJDomJar());
    doTest(ProjectRootsTraversing.PROJECT_SOURCES);
  }

  public void testModuleSources() throws Exception {
    final VirtualFile srcRoot = addSourceRoot(myModule, false);
    final VirtualFile testRoot = addSourceRoot(myModule, true);

    doTest(ProjectRootsTraversing.LIBRARIES_AND_JDK, getRtJar());
    doTest(ProjectRootsTraversing.PROJECT_SOURCES, srcRoot, testRoot);
  }

  public void testModuleOutput() throws Exception {
    setModuleOutput(myModule, false);
    setModuleOutput(myModule, true);

    doTest(ProjectRootsTraversing.LIBRARIES_AND_JDK, getRtJar());
    doTest(ProjectRootsTraversing.PROJECT_SOURCES);
  }

  public void testModuleDependency() throws Exception {
    final Module dep = createModule("b");
    setModuleOutput(dep, false);
    setModuleOutput(dep, true);
    addSourceRoot(dep, false);
    addSourceRoot(dep, true);
    addLibraryDependency(dep, createJDomLibrary());
    addModuleDependency(myModule, dep);

    doTest(ProjectRootsTraversing.PROJECT_LIBRARIES, getJDomJar());
    doTest(ProjectRootsTraversing.PROJECT_SOURCES);
  }

  private void doTest(final ProjectRootsTraversing.RootTraversePolicy policy, VirtualFile... roots) {
    assertRoots(ProjectRootsTraversing.collectRoots(myModule, policy), roots);
    assertRoots(collectByOrderEnumerator(policy), roots);
  }

  private PathsList collectByOrderEnumerator(ProjectRootsTraversing.RootTraversePolicy policy) {
    if (policy == ProjectRootsTraversing.LIBRARIES_AND_JDK) {
      return OrderEnumerator.orderEntries(myModule).withoutDepModules().withoutThisModuleContent().getPathsList();
    }
    if (policy == ProjectRootsTraversing.PROJECT_SOURCES) {
      return OrderEnumerator.orderEntries(myModule).withoutSdk().withoutLibraries().withoutDepModules().getSourcePathsList();
    }
    if (policy == ProjectRootsTraversing.PROJECT_LIBRARIES) {
      return OrderEnumerator.orderEntries(myModule).withoutSdk().withoutThisModuleContent().recursively().getPathsList();
    }
    throw new AssertionError(policy);
  }
}
