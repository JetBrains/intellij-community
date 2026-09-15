// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet;

import com.intellij.facet.impl.ui.libraries.FacetLibrariesValidatorImpl;
import com.intellij.facet.mock.MockFacet;
import com.intellij.facet.mock.MockFacetEditorContext;
import com.intellij.facet.mock.MockFacetValidatorsManager;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorsFactory;
import com.intellij.facet.ui.libraries.FacetLibrariesValidator;
import com.intellij.facet.ui.libraries.FacetLibrariesValidatorDescription;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NonNls;
import org.junit.Assert;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class FacetLibrariesValidatorTest extends FacetTestCase {
  private MockFacetValidatorsManager myValidatorsManager;
  private static final LibraryInfo ASSERTJ = new LibraryInfo("assertj.jar", (String)null, null, null, Assertions.class.getName());
  private static final LibraryInfo JUNIT = new LibraryInfo("junit.jar", (String)null, null, null, Assert.class.getName());
  private VirtualFile myAssertJJar;
  private VirtualFile myJUnitJar;
  @NonNls private static final String LIB_NAME = "lib";
  private MockFacet myFacet;
  private FacetLibrariesValidatorDescription myDescription;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myValidatorsManager = new MockFacetValidatorsManager();
    myFacet = createFacet();
    myAssertJJar = jarOf(Assertions.class);
    myJUnitJar = jarOf(Assert.class);
    myDescription = new FacetLibrariesValidatorDescription(LIB_NAME);
  }

  /**
   * Finds the jar on the test classpath. The JPS model points at the local Maven repository,
   * and a Bazel test run does not populate it.
   */
  private static VirtualFile jarOf(Class<?> aClass) {
    String path = PathManager.getJarPathForClass(aClass);
    assertNotNull(aClass.getName(), path);
    VirtualFile jar = VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(Path.of(path)));
    assertNotNull(path, jar);
    return jar;
  }

  @Override
  protected void tearDown() throws Exception {
    myValidatorsManager = null;
    myFacet = null;
    myDescription = null;
    super.tearDown();
  }


  private FacetLibrariesValidatorImpl createValidator(final LibraryInfo... infos) {
    final FacetEditorContext editorContext = new MockFacetEditorContext(myFacet);
    final FacetEditorsFactory factory = FacetEditorsFactory.getInstance();
    FacetLibrariesValidator validator = factory.createLibrariesValidator(infos, myDescription, editorContext, myValidatorsManager);
    myValidatorsManager.registerValidator(validator);
    myValidatorsManager.validate();
    return (FacetLibrariesValidatorImpl)validator;
  }

  public void testEmptyLibrariesList() {
    createValidator();
    assertNoErrors();
  }

  public void testShowError() {
    final FacetLibrariesValidator validator = createValidator(ASSERTJ);
    assertError("assertj.jar");

    validator.setRequiredLibraries(LibraryInfo.EMPTY_ARRAY);
    assertNoErrors();

    validator.setRequiredLibraries(new LibraryInfo[]{JUNIT});
    assertError("junit.jar");
  }

  public void testAddJars() {
    final FacetLibrariesValidatorImpl validator = createValidator(ASSERTJ, JUNIT);
    assertError("");

    ModuleRootModificationUtil.addModuleLibrary(myModule, myAssertJJar.getUrl());
    IndexingTestUtil.waitUntilIndexesAreReady(myProject);
    myValidatorsManager.validate();
    assertError("junit");

    ModuleRootModificationUtil.addModuleLibrary(myModule, myJUnitJar.getUrl());
    IndexingTestUtil.waitUntilIndexesAreReady(myProject);
    myValidatorsManager.validate();
    validator.onFacetInitialized(createFacet());

    final List<VirtualFile> classpath = Arrays.asList(OrderEnumerator.orderEntries(myModule).getClassesRoots());
    assertTrue(classpath.contains(myAssertJJar));
    assertTrue(classpath.contains(myJUnitJar));
  }

  public void testUnresolvedLibrary() {
    createValidator(ASSERTJ);
    assertError("assertj.jar");
  }

  public void testLibrary() {
    PsiTestUtil.addProjectLibrary(myModule, "lib1", myAssertJJar, myJUnitJar);
    createValidator(ASSERTJ, JUNIT);
    assertNoErrors();
  }

  private void assertError(@NonNls String s) {
    assertThat(myValidatorsManager.getErrorMessage()).contains(s);
  }


  private void assertNoErrors() {
    assertSame(null, myValidatorsManager.getErrorMessage());
  }
}
