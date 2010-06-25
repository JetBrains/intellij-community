package com.intellij.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author nik
 */
public class ProjectRootsTraversingTest extends ModuleRootManagerTestCase {

  public void testLibrary() throws Exception {
    addLibraryDependency(myModule, createJDomLibrary());
    assertRoots(ProjectRootsTraversing.collectRoots(myModule, ProjectRootsTraversing.LIBRARIES_AND_JDK), 
                getRtJar(), getJDomJar());
    assertRoots(ProjectRootsTraversing.collectRoots(myModule, ProjectRootsTraversing.PROJECT_LIBRARIES),
                getJDomJar());
    assertRoots(ProjectRootsTraversing.collectRoots(myModule, ProjectRootsTraversing.PROJECT_SOURCES));
  }

  public void testModuleSources() throws Exception {
    final VirtualFile srcRoot = addSourceRoot(myModule, false);
    final VirtualFile testRoot = addSourceRoot(myModule, true);
    assertRoots(ProjectRootsTraversing.collectRoots(myModule, ProjectRootsTraversing.LIBRARIES_AND_JDK),
                getRtJar());

    assertRoots(ProjectRootsTraversing.collectRoots(myModule, ProjectRootsTraversing.PROJECT_SOURCES),
                srcRoot, testRoot);
  }
  
  public void testModuleOutput() throws Exception {
    setModuleOutput(myModule, false);
    setModuleOutput(myModule, true);
    assertRoots(ProjectRootsTraversing.collectRoots(myModule, ProjectRootsTraversing.LIBRARIES_AND_JDK),
                getRtJar());
    assertRoots(ProjectRootsTraversing.collectRoots(myModule, ProjectRootsTraversing.PROJECT_SOURCES));
  }

  public void testModuleDependency() throws Exception {
    final Module dep = createModule("b");
    setModuleOutput(dep, false);
    setModuleOutput(dep, true);
    addSourceRoot(dep, false);
    addSourceRoot(dep, true);
    addLibraryDependency(dep, createJDomLibrary());
    addModuleDependency(myModule, dep);
    assertRoots(ProjectRootsTraversing.collectRoots(myModule, ProjectRootsTraversing.PROJECT_LIBRARIES),
                getJDomJar());
    assertRoots(ProjectRootsTraversing.collectRoots(myModule, ProjectRootsTraversing.PROJECT_SOURCES));
  }
}
