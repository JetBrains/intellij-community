// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.facet.impl.ui.libraries.FacetLibrariesValidatorImpl;
import com.intellij.facet.mock.MockFacet;
import com.intellij.facet.mock.MockFacetEditorContext;
import com.intellij.facet.mock.MockFacetLibrariesValidatorDescription;
import com.intellij.facet.mock.MockFacetValidatorsManager;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorsFactory;
import com.intellij.facet.ui.libraries.FacetLibrariesValidator;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class FacetLibrariesValidatorTest extends FacetTestCase {
  private MockFacetValidatorsManager myValidatorsManager;
  private static final LibraryInfo JDOM = new LibraryInfo("jdom.jar", (String)null, null, null, "org.jdom.Element");
  private static final LibraryInfo JUNIT = new LibraryInfo("junit.jar", (String)null, null, null, "junit.framework.TestCase");
  private VirtualFile myJDomJar;
  private VirtualFile myJUnitJar;
  @NonNls private static final String LIB_NAME = "lib";
  private MockFacet myFacet;
  private MockFacetLibrariesValidatorDescription myDescription;


  @NotNull
  @Override
  protected Module createModule(@NotNull final String moduleName) {
    return createModule(moduleName, StdModuleTypes.JAVA);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myValidatorsManager = new MockFacetValidatorsManager();
    myFacet = createFacet();
    myJDomJar = IntelliJProjectConfiguration.getJarFromSingleJarProjectLibrary("JDOM");
    myJUnitJar = IntelliJProjectConfiguration.getJarFromSingleJarProjectLibrary("JUnit3");
    myDescription = new MockFacetLibrariesValidatorDescription(LIB_NAME);
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
    final FacetLibrariesValidator validator = createValidator(JDOM);
    assertError("jdom.jar");

    validator.setRequiredLibraries(LibraryInfo.EMPTY_ARRAY);
    assertNoErrors();

    validator.setRequiredLibraries(new LibraryInfo[]{JUNIT});
    assertError("junit.jar");
  }

  public void testAddJars() {
    final FacetLibrariesValidatorImpl validator = createValidator(JDOM, JUNIT);
    assertError("");

    addLibraryToRoots(myJDomJar, OrderRootType.CLASSES);
    myValidatorsManager.validate();
    assertError("junit");

    addLibraryToRoots(myJUnitJar, OrderRootType.CLASSES);
    myValidatorsManager.validate();
    validator.onFacetInitialized(createFacet());

    final List<VirtualFile> classpath = Arrays.asList(OrderEnumerator.orderEntries(myModule).getClassesRoots());
    assertTrue(classpath.contains(myJDomJar));
    assertTrue(classpath.contains(myJUnitJar));
  }

  public void testUnresolvedLibrary() {
    createValidator(JDOM);
    assertError("jdom");
  }

  public void testLibrary() {
    PsiTestUtil.addProjectLibrary(myModule, "lib1", myJDomJar, myJUnitJar);
    createValidator(JDOM, JUNIT);
    assertNoErrors();
  }

  private void assertError(final @NonNls String s) {
    final String message = myValidatorsManager.getErrorMessage();
    assertNotNull(message);
    assertTrue(message.contains(s));
  }


  private void assertNoErrors() {
    assertSame(null, myValidatorsManager.getErrorMessage());
  }
}
