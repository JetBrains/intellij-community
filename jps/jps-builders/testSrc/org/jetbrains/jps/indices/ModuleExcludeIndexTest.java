/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.indices;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.indices.impl.ModuleExcludeIndexImpl;
import org.jetbrains.jps.model.JpsJavaModelTestCase;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.Collection;

/**
 * @author nik
 */
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
    assertSameElements(getModuleExcludes(module2), out);
  }

  public void testModuleOutput() {
    File out = new File(myRoot, "out");
    JpsModule module = addModule();
    JpsJavaModuleExtension extension = getJavaService().getOrCreateModuleExtension(module);
    extension.setExcludeOutput(true);
    extension.setOutputUrl(JpsPathUtil.pathToUrl(out.getAbsolutePath()));

    assertNotExcluded(myRoot);
    assertExcluded(out);
    assertSameElements(getModuleExcludes(module), out);

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
    assertSameElements(getModuleExcludes(module), exc);
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
    assertSameElements(getModuleExcludes(outer), inner1Root, inner2Root);
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
    assertSameElements(getModuleExcludes(outer), exc, innerRoot);
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
    module.addExcludePattern(JpsPathUtil.pathToUrl(root1.getAbsolutePath()), "*.txt");
    module.addExcludePattern(JpsPathUtil.pathToUrl(root2.getAbsolutePath()), "out");
    assertExcluded(new File(root1, "a.txt"));
    assertExcluded(new File(root1, "dir/a.txt"));
    assertNotExcluded(new File(root1, "A.java"));
    assertNotExcluded(new File(root2, "a.txt"));
    assertExcluded(new File(root2, "out"));
    assertExcluded(new File(root2, "out/A.java"));
    assertExcluded(new File(root2, "dir/out/A.java"));
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

  private Collection<File> getModuleExcludes(JpsModule module) {
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
