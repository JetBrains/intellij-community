package com.intellij.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;

/**
 * @author nik
 */
public class OrderEntriesTest extends ModuleRootManagerTestCase {
  public void testLibrary() {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());
    assertOrderFiles(OrderRootType.CLASSES, getRtJarJdk17(), getJDomJar());
    assertOrderFiles(OrderRootType.SOURCES, getJDomSources());
  }

  public void testModuleSources() throws Exception {
    final VirtualFile srcRoot = addSourceRoot(myModule, false);
    final VirtualFile testRoot = addSourceRoot(myModule, true);

    assertOrderFiles(OrderRootType.CLASSES, getRtJarJdk17());
    assertOrderFiles(OrderRootType.SOURCES, srcRoot, testRoot);
  }

  public void testLibraryScope() {
    ModuleRootModificationUtil.addDependency(myModule, createJDomLibrary(), DependencyScope.TEST, false);

    assertOrderFiles(OrderRootType.CLASSES, getRtJarJdk17(), getJDomJar());
    assertOrderFiles(OrderRootType.SOURCES, getJDomSources());
  }

  public void testModuleDependency() throws Exception {
    final Module dep = createModule("dep");
    final VirtualFile srcRoot = addSourceRoot(dep, false);
    final VirtualFile testRoot = addSourceRoot(dep, true);
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.COMPILE, false);

    assertOrderFiles(OrderRootType.CLASSES, getRtJarJdk17(), getJDomJar());
    assertOrderFiles(OrderRootType.SOURCES, srcRoot, testRoot, getJDomSources());
  }

  public void testModuleDependencyScope() {
    final Module dep = createModule("dep");
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.TEST, true);

    assertOrderFiles(OrderRootType.CLASSES, getRtJarJdk17(), getJDomJar());
    assertOrderFiles(OrderRootType.SOURCES, getJDomSources());
  }

  public void testNotExportedLibraryDependency() {
    final Module dep = createModule("dep");
    ModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), DependencyScope.COMPILE, false);
    ModuleRootModificationUtil.addDependency(myModule, dep, DependencyScope.COMPILE, false);

    assertOrderFiles(OrderRootType.CLASSES, getRtJarJdk17());
    assertOrderFiles(OrderRootType.SOURCES);
  }

  private void assertOrderFiles(final OrderRootType type, VirtualFile... files) {
    assertRoots(collectByOrderEnumerator(type), files);
  }

  private PathsList collectByOrderEnumerator(OrderRootType type) {
    final OrderEnumerator base = OrderEnumerator.orderEntries(myModule);
    if (type == OrderRootType.CLASSES) {
      return base.withoutModuleSourceEntries().recursively().exportedOnly().getPathsList();
    }
    if (type == OrderRootType.SOURCES) {
      return base.recursively().exportedOnly().getSourcePathsList();
    }
    throw new AssertionError(type);
  }
}

