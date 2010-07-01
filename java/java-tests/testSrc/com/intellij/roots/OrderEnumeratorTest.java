package com.intellij.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;

import static com.intellij.openapi.roots.OrderEnumerator.orderEntries;

/**
 * @author nik
 */
public class OrderEnumeratorTest extends ModuleRootManagerTestCase {

  public void testLibrary() throws Exception {
    addLibraryDependency(myModule, createJDomLibrary());

    assertClassRoots(orderEntries(myModule), getRtJar(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutSdk(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().productionOnly().runtimeOnly(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutLibraries(), getRtJar());
    assertSourceRoots(orderEntries(myModule), getJDomSources());
  }

  public void testModuleSources() throws Exception {
    final VirtualFile srcRoot = addSourceRoot(myModule, false);
    final VirtualFile testRoot = addSourceRoot(myModule, true);
    final VirtualFile output = setModuleOutput(myModule, false);
    final VirtualFile testOutput = setModuleOutput(myModule, true);

    assertClassRoots(orderEntries(myModule).withoutSdk(), testOutput, output);
    assertClassRoots(orderEntries(myModule).withoutSdk().productionOnly(), output);
    assertSourceRoots(orderEntries(myModule), srcRoot, testRoot);
    assertSourceRoots(orderEntries(myModule).productionOnly(), srcRoot);
  }

  public void testLibraryScope() throws Exception {
    addLibraryDependency(myModule, createJDomLibrary(), DependencyScope.RUNTIME, false);

    assertClassRoots(orderEntries(myModule).withoutSdk(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().exportedOnly());
    assertClassRoots(orderEntries(myModule).withoutSdk().compileOnly());
  }

  public void testModuleDependency() throws Exception {
    final Module dep = createModule("dep");
    final VirtualFile srcRoot = addSourceRoot(dep, false);
    final VirtualFile testRoot = addSourceRoot(dep, true);
    final VirtualFile output = setModuleOutput(dep, false);
    final VirtualFile testOutput = setModuleOutput(dep, true);
    addLibraryDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, true);
    addModuleDependency(myModule, dep, DependencyScope.COMPILE, true);

    assertClassRoots(orderEntries(myModule).withoutSdk(), testOutput, output);
    assertClassRoots(orderEntries(myModule).withoutSdk().recursively(), testOutput, output, getJDomJar());
    assertSourceRoots(orderEntries(myModule), srcRoot, testRoot);
    assertSourceRoots(orderEntries(myModule).recursively(), srcRoot, testRoot, getJDomSources());
  }

  public void testModuleDependencyScope() throws Exception {
    final Module dep = createModule("dep");
    addLibraryDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, true);
    addModuleDependency(myModule, dep, DependencyScope.TEST, true);

    assertClassRoots(orderEntries(myModule).withoutSdk());
    assertClassRoots(orderEntries(myModule).withoutSdk().recursively(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().exportedOnly().recursively(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().productionOnly().recursively());

    assertClassRoots(ProjectRootManager.getInstance(myProject).orderEntries().withoutSdk(), getJDomJar());
    assertClassRoots(ProjectRootManager.getInstance(myProject).orderEntries().withoutSdk().productionOnly(), getJDomJar());
  }
  
  public void testNotExportedLibrary() throws Exception {
    final Module dep = createModule("dep");
    addLibraryDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, false);
    addLibraryDependency(myModule, createAsmLibrary(), DependencyScope.COMPILE, false);
    addModuleDependency(myModule, dep, DependencyScope.COMPILE, false);

    assertClassRoots(orderEntries(myModule).withoutSdk(), getAsmJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().recursively(), getAsmJar(), getJDomJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().recursively().exportedOnly(), getAsmJar());
    assertClassRoots(orderEntries(myModule).withoutSdk().exportedOnly().recursively());
  }

  public void testJdkIsNotExported() throws Exception {
    assertClassRoots(orderEntries(myModule).exportedOnly());
  }

  private static void assertClassRoots(final OrderEnumerator enumerator, VirtualFile... files) {
    assertRoots(enumerator.getPathsList(), files);
  }

  private static void assertSourceRoots(final OrderEnumerator enumerator, VirtualFile... files) {
    final PathsList result = new PathsList();
    enumerator.sources().collectPaths(result);
    assertRoots(result, files);
  }

}
