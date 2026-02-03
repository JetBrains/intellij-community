// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.indices;

import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.indices.impl.ModuleExcludeIndexImpl;
import org.jetbrains.jps.model.JpsJavaModelTestCase;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleExcludeIndexTest extends JpsJavaModelTestCase {
  private File myRoot;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRoot = FileUtil.createTempDirectory("excludes", null);
  }

  public void testProjectOutput() {
    File out = new File(myRoot, "out");
    getJavaService().getOrCreateProjectExtension(myProject).setOutputUrl(JpsPathUtil.pathToUrl(out.getAbsolutePath()));
    JpsModule module1 = addModule();
    getJavaService().getOrCreateModuleExtension(module1).setInheritOutput(true);
    JpsModule module2 = addModule();
    addContentRoot(module2, out);
    getJavaService().getOrCreateModuleExtension(module2).setInheritOutput(true);

    assertNotExcluded(myRoot);
    assertExcluded(out);
    assertEmpty(getModuleExcludes(module1));
    assertThat(getModuleExcludes(module2)).containsExactlyInAnyOrder(out.toPath());
  }

  public void testModuleOutput() {
    File out = new File(myRoot, "out");
    JpsModule module = addModule();
    JpsJavaModuleExtension extension = getJavaService().getOrCreateModuleExtension(module);
    extension.setExcludeOutput(true);
    extension.setOutputUrl(JpsPathUtil.pathToUrl(out.getAbsolutePath()));

    assertNotExcluded(myRoot);
    assertExcluded(out);
    assertThat(getModuleExcludes(module)).containsExactlyInAnyOrder(out.toPath());

    extension.setExcludeOutput(false);
    assertNotExcluded(out);
    assertEmpty(getModuleExcludes(module));
  }

  public void testExcludedFolder() {
    File exc = new File(myRoot, "exc");
    JpsModule module = addModule();
    addExcludedRoot(module, exc);

    assertNotExcluded(myRoot);
    assertExcluded(exc);
    assertThat(getModuleExcludes(module)).containsExactlyInAnyOrder(exc.toPath());
  }

  public void testContentRootUnderExcluded() {
    File contentRoot = new File(myRoot, "project");
    File excluded = new File(contentRoot, "exc");
    File unexcluded = new File(excluded, "src");
    JpsModule module = addModule();
    addContentRoot(module, contentRoot);
    addExcludedRoot(module, excluded);
    addContentRoot(module, unexcluded);
    assertNotExcluded(contentRoot);
    assertExcluded(excluded);
    assertNotExcluded(unexcluded);
    ModuleExcludeIndexImpl index = createIndex();
    assertFalse(index.isExcludedFromModule(contentRoot, module));
    assertTrue(index.isExcludedFromModule(excluded, module));
    assertFalse(index.isExcludedFromModule(unexcluded, module));
  }

  public void testInnerModules() {
    File outerRoot = new File(myRoot, "outer");
    File inner1Root = new File(outerRoot, "inner1");
    File inner2Root = new File(outerRoot, "inner2");
    JpsModule outer = addModule("outer");
    addContentRoot(outer, outerRoot);
    JpsModule inner1 = addModule("inner1");
    JpsModule inner2 = addModule("inner2");
    addContentRoot(inner1, inner1Root);
    addContentRoot(inner2, inner2Root);
    assertNotExcluded(outerRoot);
    assertNotExcluded(inner1Root);
    assertNotExcluded(inner2Root);
    assertThat(getModuleExcludes(outer)).containsExactlyInAnyOrder(inner1Root.toPath(), inner2Root.toPath());
    assertEmpty(getModuleExcludes(inner1));
    assertEmpty(getModuleExcludes(inner2));
    ModuleExcludeIndexImpl index = createIndex();
    assertTrue(index.isExcludedFromModule(inner1Root, outer));
    assertTrue(index.isExcludedFromModule(inner2Root, outer));
    assertFalse(index.isExcludedFromModule(inner1Root, inner1));
    assertFalse(index.isExcludedFromModule(inner2Root, inner2));
  }

  public void testInnerModuleUnderExcludedRoot() {
    File outerRoot = new File(myRoot, "outer");
    File exc = new File(outerRoot, "exc");
    File innerRoot = new File(exc, "inner");
    JpsModule outer = addModule("outer");
    addContentRoot(outer, outerRoot);
    addExcludedRoot(outer, exc);
    JpsModule inner = addModule("inner");
    addContentRoot(inner, innerRoot);
    assertNotExcluded(outerRoot);
    assertNotExcluded(innerRoot);
    assertThat(getModuleExcludes(outer)).containsExactlyInAnyOrder(exc.toPath(), innerRoot.toPath());
    assertEmpty(getModuleExcludes(inner));
    ModuleExcludeIndexImpl index = createIndex();
    assertTrue(index.isExcludedFromModule(innerRoot, outer));
    assertFalse(index.isExcludedFromModule(innerRoot, inner));
  }

  public void testSourceRootUnderExcluded() {
    File project = new File(myRoot, "project");
    File exc = new File(project, "exc");
    File src = new File(exc, "src");
    JpsModule module = addModule();
    addContentRoot(module, project);
    addExcludedRoot(module, exc);
    addSourceRoot(module, src);
    assertNotExcluded(src);

    addExcludedRoot(module, src);
    assertExcluded(src);
  }

  public void testExcludeByPattern() {
    File root1 = new File(myRoot, "root1");
    File root2 = new File(myRoot, "root2");
    JpsModule module = addModule();
    addContentRoot(module, root1);
    addContentRoot(module, root2);
    assertSame(FileFilters.EVERYTHING, createIndex().getModuleFileFilterHonorExclusionPatterns(module));
    module.addExcludePattern(JpsPathUtil.pathToUrl(root1.getAbsolutePath()), "*.txt");
    module.addExcludePattern(JpsPathUtil.pathToUrl(root2.getAbsolutePath()), "out");

    assertExcluded(new File(root1, "a.txt"));
    assertExcluded(new File(root1, "dir/a.txt"));
    assertNotExcluded(new File(root1, "A.java"));
    assertNotExcluded(new File(root2, "a.txt"));
    assertExcluded(new File(root2, "out"));
    assertExcluded(new File(root2, "out/A.java"));
    assertExcluded(new File(root2, "dir/out/A.java"));

    FileFilter moduleFilter = createIndex().getModuleFileFilterHonorExclusionPatterns(module);
    assertTrue(moduleFilter.accept(new File(root1, "A.java")));
    assertTrue(moduleFilter.accept(new File(root2, "a.txt")));
    assertFalse(moduleFilter.accept(new File(root1, "a.txt")));
    assertFalse(moduleFilter.accept(new File(root1, "dir/a.txt")));
  }

  private static void addSourceRoot(JpsModule module, File src) {
    module.addSourceRoot(JpsPathUtil.pathToUrl(src.getAbsolutePath()), JavaSourceRootType.SOURCE);
  }

  private static void addExcludedRoot(JpsModule module, File root) {
    module.getExcludeRootsList().addUrl(JpsPathUtil.pathToUrl(root.getAbsolutePath()));
  }

  private static void addContentRoot(JpsModule module, File root) {
    module.getContentRootsList().addUrl(JpsPathUtil.pathToUrl(root.getAbsolutePath()));
  }

  private Collection<Path> getModuleExcludes(JpsModule module) {
    return createIndex().getModuleExcludes(module);
  }

  private void assertExcluded(File file) {
    assertTrue(createIndex().isExcluded(file));
  }

  private void assertNotExcluded(File file) {
    assertFalse(createIndex().isExcluded(file));
  }

  @NotNull
  private ModuleExcludeIndexImpl createIndex() {
    return new ModuleExcludeIndexImpl(myModel);
  }
}
