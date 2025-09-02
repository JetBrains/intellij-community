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
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class FacetLibrariesValidatorTest extends FacetTestCase {
  private MockFacetValidatorsManager myValidatorsManager;
  private static final LibraryInfo
    FAST_UTIL = new LibraryInfo("fastutil.jar", (String)null, null, null, "it.unimi.dsi.fastutil.objects.ObjectOpenHashSet");
  private static final LibraryInfo HASH4J = new LibraryInfo("hash4j.jar", (String)null, null, null, "com.dynatrace.hash4j.hashing.Hasher");
  private VirtualFile myFastUtilJar;
  private VirtualFile myHash4jJar;
  @NonNls private static final String LIB_NAME = "lib";
  private MockFacet myFacet;
  private FacetLibrariesValidatorDescription myDescription;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myValidatorsManager = new MockFacetValidatorsManager();
    myFacet = createFacet();
    myFastUtilJar = IntelliJProjectConfiguration.getJarFromSingleJarProjectLibrary("fastutil-min");
    myHash4jJar = IntelliJProjectConfiguration.getJarFromSingleJarProjectLibrary("hash4j");
    myDescription = new FacetLibrariesValidatorDescription(LIB_NAME);
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
    final FacetLibrariesValidator validator = createValidator(FAST_UTIL);
    assertError("fastutil.jar");

    validator.setRequiredLibraries(LibraryInfo.EMPTY_ARRAY);
    assertNoErrors();

    validator.setRequiredLibraries(new LibraryInfo[]{HASH4J});
    assertError("hash4j.jar");
  }

  public void testAddJars() {
    final FacetLibrariesValidatorImpl validator = createValidator(FAST_UTIL, HASH4J);
    assertError("");

    ModuleRootModificationUtil.addModuleLibrary(myModule, myFastUtilJar.getUrl());
    IndexingTestUtil.waitUntilIndexesAreReady(myProject);
    myValidatorsManager.validate();
    assertError("hash4j");

    ModuleRootModificationUtil.addModuleLibrary(myModule, myHash4jJar.getUrl());
    IndexingTestUtil.waitUntilIndexesAreReady(myProject);
    myValidatorsManager.validate();
    validator.onFacetInitialized(createFacet());

    final List<VirtualFile> classpath = Arrays.asList(OrderEnumerator.orderEntries(myModule).getClassesRoots());
    assertTrue(classpath.contains(myFastUtilJar));
    assertTrue(classpath.contains(myHash4jJar));
  }

  public void testUnresolvedLibrary() {
    createValidator(FAST_UTIL);
    assertError("fastutil.jar");
  }

  public void testLibrary() {
    PsiTestUtil.addProjectLibrary(myModule, "lib1", myFastUtilJar, myHash4jJar);
    createValidator(FAST_UTIL, HASH4J);
    assertNoErrors();
  }

  private void assertError(@NonNls String s) {
    assertThat(myValidatorsManager.getErrorMessage()).contains(s);
  }


  private void assertNoErrors() {
    assertSame(null, myValidatorsManager.getErrorMessage());
  }
}
