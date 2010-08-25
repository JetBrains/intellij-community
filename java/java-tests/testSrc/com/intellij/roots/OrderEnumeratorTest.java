package com.intellij.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootsEnumerator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

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

    assertEnumeratorRoots(orderEntries(myModule).withoutSdk().classes().withoutSelfModuleOutput(), output);
    assertEnumeratorRoots(orderEntries(myModule).withoutSdk().productionOnly().classes().withoutSelfModuleOutput());
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
    assertEnumeratorRoots(orderEntries(myModule).withoutSdk().recursively().classes().withoutSelfModuleOutput(), testOutput, output, getJDomJar());
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

  public void testCaching() throws Exception {
    final VirtualFile[] roots = orderEntries(myModule).classes().usingCache().getRoots();
    assertOrderedEquals(roots, getRtJar());
    assertSame(roots, orderEntries(myModule).classes().usingCache().getRoots());
    final VirtualFile[] rootsWithoutSdk = orderEntries(myModule).withoutSdk().classes().usingCache().getRoots();
    assertEmpty(rootsWithoutSdk);
    assertSame(roots, orderEntries(myModule).classes().usingCache().getRoots());
    assertSame(rootsWithoutSdk, orderEntries(myModule).withoutSdk().classes().usingCache().getRoots());

    addLibraryDependency(myModule, createJDomLibrary());

    assertRoots(orderEntries(myModule).classes().usingCache().getPathsList(), getRtJar(), getJDomJar());
    assertRoots(orderEntries(myModule).withoutSdk().classes().usingCache().getPathsList(), getJDomJar());
  }
  
  public void testCachingUrls() throws Exception {
    final String[] urls = orderEntries(myModule).classes().usingCache().getUrls();
    assertOrderedEquals(urls, getRtJar().getUrl());
    assertSame(urls, orderEntries(myModule).classes().usingCache().getUrls());

    final String[] sourceUrls = orderEntries(myModule).sources().usingCache().getUrls();
    assertEmpty(sourceUrls);
    assertSame(urls, orderEntries(myModule).classes().usingCache().getUrls());
    assertSame(sourceUrls, orderEntries(myModule).sources().usingCache().getUrls());

    addLibraryDependency(myModule, createJDomLibrary());
    assertOrderedEquals(orderEntries(myModule).classes().usingCache().getUrls(), getRtJar().getUrl(), getJDomJar().getUrl());
    assertOrderedEquals(orderEntries(myModule).sources().usingCache().getUrls(), getJDomSources().getUrl());
  }

  private static void assertClassRoots(final OrderEnumerator enumerator, VirtualFile... files) {
    assertEnumeratorRoots(enumerator.classes(), files);
  }

  private static void assertSourceRoots(final OrderEnumerator enumerator, VirtualFile... files) {
    assertEnumeratorRoots(enumerator.sources(), files);
  }

  private static void assertEnumeratorRoots(OrderRootsEnumerator rootsEnumerator, VirtualFile... files) {
    assertOrderedEquals(rootsEnumerator.getRoots(), files);
    List<String> expectedUrls = new ArrayList<String>();
    for (VirtualFile file : files) {
      expectedUrls.add(file.getUrl());
    }
    assertOrderedEquals(rootsEnumerator.getUrls(), ArrayUtil.toStringArray(expectedUrls));
  }

}
