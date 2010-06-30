package com.intellij.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;

/**
 * @author nik
 */
public class OrderEntriesTest extends ModuleRootManagerTestCase {
  public void testLibrary() throws Exception {
    addLibraryDependency(myModule, createJDomLibrary());
    assertOrderFiles(OrderRootType.CLASSES, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.SOURCES, getJDomSources());
    assertOrderFiles(OrderRootType.CLASSES_AND_OUTPUT, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.COMPILATION_CLASSES, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.PRODUCTION_COMPILATION_CLASSES, getRtJar(), getJDomJar());
  }

  public void testModuleSources() throws Exception {
    final VirtualFile srcRoot = addSourceRoot(myModule, false);
    final VirtualFile testRoot = addSourceRoot(myModule, true);
    final VirtualFile output = setModuleOutput(myModule, false);
    final VirtualFile testOutput = setModuleOutput(myModule, true);

    assertOrderFiles(OrderRootType.CLASSES, getRtJar());
    assertOrderFiles(OrderRootType.SOURCES, srcRoot, testRoot);
    assertOrderFiles(OrderRootType.CLASSES_AND_OUTPUT, getRtJar(), testOutput, output);
    assertOrderFiles(OrderRootType.COMPILATION_CLASSES, getRtJar(), testOutput, output);
    assertOrderFiles(OrderRootType.PRODUCTION_COMPILATION_CLASSES, getRtJar(), output);
  }

  public void testLibraryScope() throws Exception {
    addLibraryDependency(myModule, createJDomLibrary(), DependencyScope.TEST, false);

    assertOrderFiles(OrderRootType.CLASSES, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.SOURCES, getJDomSources());
    assertOrderFiles(OrderRootType.CLASSES_AND_OUTPUT, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.COMPILATION_CLASSES, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.PRODUCTION_COMPILATION_CLASSES, getRtJar());
  }

  public void testModuleDependency() throws Exception {
    final Module dep = createModule("dep");
    final VirtualFile srcRoot = addSourceRoot(dep, false);
    final VirtualFile testRoot = addSourceRoot(dep, true);
    final VirtualFile output = setModuleOutput(dep, false);
    final VirtualFile testOutput = setModuleOutput(dep, true);
    addLibraryDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, true);
    addModuleDependency(myModule, dep);

    assertOrderFiles(OrderRootType.CLASSES, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.SOURCES, srcRoot, testRoot, getJDomSources());
    assertOrderFiles(OrderRootType.CLASSES_AND_OUTPUT, getRtJar(), testOutput, output, getJDomJar());
    assertOrderFiles(OrderRootType.COMPILATION_CLASSES, getRtJar(), testOutput, output, getJDomJar());
    assertOrderFiles(OrderRootType.PRODUCTION_COMPILATION_CLASSES, getRtJar(), output, getJDomJar());
  }

  public void testModuleDependencyScope() throws Exception {
    final Module dep = createModule("dep");
    addLibraryDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, true);
    addModuleDependency(myModule, dep, DependencyScope.TEST, true);

    assertOrderFiles(OrderRootType.CLASSES, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.SOURCES, getJDomSources());
    assertOrderFiles(OrderRootType.CLASSES_AND_OUTPUT, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.COMPILATION_CLASSES, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.PRODUCTION_COMPILATION_CLASSES, getRtJar());
  }

  public void testNotExportedLibraryDependency() throws Exception {
    final Module dep = createModule("dep");
    addLibraryDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, false);
    addModuleDependency(myModule, dep);

    assertOrderFiles(OrderRootType.CLASSES, getRtJar());
    assertOrderFiles(OrderRootType.SOURCES);
    assertOrderFiles(OrderRootType.CLASSES_AND_OUTPUT, getRtJar(), getJDomJar());
    assertOrderFiles(OrderRootType.COMPILATION_CLASSES, getRtJar());
    assertOrderFiles(OrderRootType.PRODUCTION_COMPILATION_CLASSES, getRtJar());
  }

  private void assertOrderFiles(final OrderRootType type, VirtualFile... files) {
    assertOrderedEquals(ModuleRootManager.getInstance(myModule).getFiles(type), files);
    assertRoots(collectByOrderEnumerator(type), files);
  }

  private PathsList collectByOrderEnumerator(OrderRootType type) {
    final OrderEnumerator base = OrderEnumerator.orderEntries(myModule);
    if (type == OrderRootType.CLASSES_AND_OUTPUT) {
      return base.recursively().getPathsList();
    }
    if (type == OrderRootType.COMPILATION_CLASSES) {
      return base.recursively().exportedOnly().getPathsList();
    }
    if (type == OrderRootType.PRODUCTION_COMPILATION_CLASSES) {
      return base.productionOnly().recursively().exportedOnly().getPathsList();
    }
    if (type == OrderRootType.CLASSES) {
      return base.withoutModuleSourceEntries().recursively().exportedOnly().getPathsList();
    }
    if (type == OrderRootType.SOURCES) {
      return base.recursively().exportedOnly().getSourcePathsList();
    }
    throw new AssertionError(type);
  }
}

