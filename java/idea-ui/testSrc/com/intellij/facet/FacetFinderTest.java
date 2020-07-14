// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.facet.mock.MockFacet;
import com.intellij.facet.mock.MockFacetType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

public class FacetFinderTest extends FacetTestCase {
  private FacetFinder myFacetFinder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFacetFinder = FacetFinder.getInstance(myProject);
  }

  @Override
  public void tearDown() throws Exception {
    myFacetFinder = null;

    super.tearDown();
  }

  public void testAddRemoveRoot() {
    final MockFacet mockFacet = addFacet();
    final VirtualFile configFile = findFile("config.xml");

    assertNull(findFacet(configFile));
    mockFacet.addRoot(configFile);
    assertSame(mockFacet, findFacet(configFile));
    assertNull(myFacetFinder.findFacet(configFile, new FacetTypeId<MockFacet>("mock")));

    final VirtualFile root = findFile("dir");
    mockFacet.addRoot(root);
    assertSame(mockFacet, findFacet(findFile("dir/file.txt")));

    mockFacet.removeRoot(configFile);
    assertNull(findFacet(configFile));
    assertSame(mockFacet, findFacet(findFile("dir/file.txt")));

    mockFacet.removeRoot(root);
    assertNull(findFacet(findFile("dir/file.txt")));
  }

  public void testAddRemoveFacet() {
    final VirtualFile configFile = findFile("config.xml");

    final MockFacet facet = createFacet();
    facet.addRoot(configFile);

    assertNull(findFacet(configFile));
    addFacet(facet);
    assertSame(facet, findFacet(configFile));

    removeFacet(facet);
    assertNull(findFacet(configFile));
  }

  public void testAddRemoveModule() throws Exception {
    final VirtualFile file = findFile("../module/src/pack/MyClass.java");

    assertNull(findFacet(file));
    File imlFile = PathManagerEx.findFileUnderCommunityHome("java/java-tests/testData/facet/module/MyFacetModule.iml");
    Module module = WriteAction.compute(() -> ModuleManager.getInstance(myProject).loadModule(imlFile.toPath()));
    MockFacet facet = findFacet(file);
    assertNotNull(facet);
    assertSame(module, facet.getModule());

    ModuleManager.getInstance(myProject).disposeModule(module);
    assertNull(findFacet(file));
  }

  private MockFacet findFacet(final VirtualFile root) {
    return myFacetFinder.findFacet(root, MockFacetType.ID);
  }

  private static VirtualFile findFile(final String path) {
    final File file = new File(PathManagerEx.getTestDataPath(), "facet/findByFile/" + path);
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    assertNotNull(virtualFile);
    return virtualFile;
  }
}
