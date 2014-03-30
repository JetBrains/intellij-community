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
package org.jetbrains.jps.model;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.util.JpsPathUtil;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.jetbrains.jps.model.java.JpsJavaExtensionService.dependencies;


/**
 * @author nik
 */
public class JpsDependenciesEnumeratorTest extends JpsJavaModelTestCase {
  private JpsModule myModule;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File home = PathManagerEx.findFileUnderCommunityHome("java/mockJDK-1.7");
    JpsTypedLibrary<JpsSdk<JpsDummyElement>> jdk =
      myModel.getGlobal().addSdk("mockJDK-1.7", home.getAbsolutePath(), "1.7", JpsJavaSdkType.INSTANCE);
    jdk.addRoot(getRtJar(), JpsOrderRootType.COMPILED);
    myModule = addModule();
    myModule.getSdkReferencesTable().setSdkReference(JpsJavaSdkType.INSTANCE, jdk.getProperties().createReference());
    myModule.getDependenciesList().addSdkDependency(JpsJavaSdkType.INSTANCE);
  }

  public void testLibrary() throws Exception {
    JpsModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());

    assertClassRoots(dependencies(myModule), getRtJar(), getJDomJar());
    assertClassRoots(dependencies(myModule).withoutSdk(), getJDomJar());
    assertClassRoots(dependencies(myModule).withoutSdk().productionOnly().runtimeOnly(), getJDomJar());
    assertClassRoots(dependencies(myModule).withoutLibraries(), getRtJar());
    assertSourceRoots(dependencies(myModule), getJDomSources());
  }

  private String getJDomSources() {
    return getJarUrlFromLibDir("src/jdom.zip");
  }

  private String getJDomJar() {
    return getJarUrlFromLibDir("jdom.jar");
  }

  private String getAsmJar() {
    return getJarUrlFromLibDir("asm.jar");
  }

  private static String getJarUrlFromLibDir(final String relativePath) {
    return JpsPathUtil.getLibraryRootUrl(PathManager.findFileInLibDirectory(relativePath));
  }

  private static String getRtJar() {
    return JpsPathUtil.getLibraryRootUrl(PathManagerEx.findFileUnderCommunityHome("java/mockJDK-1.7/jre/lib/rt.jar"));
  }

  private JpsLibrary createJDomLibrary() {
    JpsLibrary library = addLibrary("jdom");
    library.addRoot(getJDomJar(), JpsOrderRootType.COMPILED);
    library.addRoot(getJDomSources(), JpsOrderRootType.SOURCES);
    return library;
  }

  private JpsLibrary createAsmLibrary() {
    JpsLibrary library = addLibrary("asm");
    library.addRoot(getAsmJar(), JpsOrderRootType.COMPILED);
    return library;
  }

  public void testModuleSources() throws Exception {
    final String srcRoot = addSourceRoot(myModule, false);
    final String testRoot = addSourceRoot(myModule, true);
    final String output = setModuleOutput(myModule, false);
    final String testOutput = setModuleOutput(myModule, true);

    assertClassRoots(dependencies(myModule).withoutSdk(), testOutput, output);
    assertClassRoots(dependencies(myModule).withoutSdk().productionOnly(), output);
    assertSourceRoots(dependencies(myModule), srcRoot, testRoot);
    assertSourceRoots(dependencies(myModule).productionOnly(), srcRoot);

    assertEnumeratorRoots(dependencies(myModule).withoutSdk().classes().withoutSelfModuleOutput(), output);
    assertEnumeratorRoots(dependencies(myModule).withoutSdk().productionOnly().classes().withoutSelfModuleOutput());
  }

  public void testLibraryScope() throws Exception {
    JpsLibraryDependency dependency = myModule.getDependenciesList().addLibraryDependency(createJDomLibrary());
    getJavaService().getOrCreateDependencyExtension(dependency).setScope(JpsJavaDependencyScope.RUNTIME);
    JpsModuleRootModificationUtil.addDependency(myModule, createJDomLibrary(), JpsJavaDependencyScope.RUNTIME, false);

    assertClassRoots(dependencies(myModule).withoutSdk(), getJDomJar());
    assertClassRoots(dependencies(myModule).withoutSdk().exportedOnly());
    assertClassRoots(dependencies(myModule).withoutSdk().compileOnly());
  }

  public void testModuleDependency() throws Exception {
    final JpsModule dep = addModule("dep");
    final String depSrcRoot = addSourceRoot(dep, false);
    final String depTestRoot = addSourceRoot(dep, true);
    final String depOutput = setModuleOutput(dep, false);
    final String depTestOutput = setModuleOutput(dep, true);
    JpsModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), JpsJavaDependencyScope.COMPILE, true);
    JpsModuleRootModificationUtil.addDependency(myModule, dep, JpsJavaDependencyScope.COMPILE, true);

    final String srcRoot = addSourceRoot(myModule, false);
    final String testRoot = addSourceRoot(myModule, true);
    final String output = setModuleOutput(myModule, false);
    final String testOutput = setModuleOutput(myModule, true);

    assertClassRoots(dependencies(myModule).withoutSdk(), testOutput, output, depTestOutput, depOutput);
    assertClassRoots(dependencies(myModule).withoutSdk().recursively(), testOutput, output, depTestOutput, depOutput, getJDomJar());
    assertSourceRoots(dependencies(myModule), srcRoot, testRoot, depSrcRoot, depTestRoot);
    assertSourceRoots(dependencies(myModule).recursively(), srcRoot, testRoot, depSrcRoot, depTestRoot, getJDomSources());

    assertClassRoots(dependencies(myModule).withoutSdk().withoutModuleSourceEntries().recursively(), getJDomJar());
    assertSourceRoots(dependencies(myModule).withoutSdk().withoutModuleSourceEntries().recursively(), getJDomSources());
    assertEnumeratorRoots(dependencies(myModule).withoutSdk().withoutModuleSourceEntries().recursively().classes(), getJDomJar());
    assertEnumeratorRoots(dependencies(myModule).withoutSdk().withoutModuleSourceEntries().recursively().sources(), getJDomSources());

    assertEnumeratorRoots(dependencies(myModule).withoutSdk().recursively().classes().withoutSelfModuleOutput(),
                          output, depTestOutput, depOutput, getJDomJar());
    assertEnumeratorRoots(dependencies(myModule).productionOnly().withoutSdk().recursively().classes().withoutSelfModuleOutput(),
                          depOutput, getJDomJar());

    assertClassRoots(dependencies(myModule).withoutSdk().withoutDepModules().withoutModuleSourceEntries().recursively(), getJDomJar());
    assertEnumeratorRoots(
      dependencies(myModule).productionOnly().withoutSdk().withoutDepModules().withoutModuleSourceEntries().recursively().classes(),
      getJDomJar());
    assertClassRoots(dependencies(myModule).withoutSdk().withoutDepModules().withoutModuleSourceEntries());
    assertEnumeratorRoots(dependencies(myModule).productionOnly().withoutModuleSourceEntries().withoutSdk().withoutDepModules().classes());
  }

  public void testModuleJpsJavaDependencyScope() throws Exception {
    final JpsModule dep = addModule("dep");
    JpsModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), JpsJavaDependencyScope.COMPILE, true);
    JpsModuleRootModificationUtil.addDependency(myModule, dep, JpsJavaDependencyScope.TEST, true);

    assertClassRoots(dependencies(myModule).withoutSdk());
    assertClassRoots(dependencies(myModule).withoutSdk().recursively(), getJDomJar());
    assertClassRoots(dependencies(myModule).withoutSdk().exportedOnly().recursively(), getJDomJar());
    assertClassRoots(dependencies(myModule).withoutSdk().productionOnly().recursively());

    assertClassRoots(dependencies(myProject).withoutSdk(), getJDomJar());
    assertClassRoots(dependencies(myProject).withoutSdk().productionOnly(), getJDomJar());
  }

  public void testNotExportedLibrary() throws Exception {
    final JpsModule dep = addModule("dep");
    JpsModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), JpsJavaDependencyScope.COMPILE, false);
    JpsModuleRootModificationUtil.addDependency(myModule, createAsmLibrary(), JpsJavaDependencyScope.COMPILE, false);
    JpsModuleRootModificationUtil.addDependency(myModule, dep, JpsJavaDependencyScope.COMPILE, false);

    assertClassRoots(dependencies(myModule).withoutSdk(), getAsmJar());
    assertClassRoots(dependencies(myModule).withoutSdk().recursively(), getAsmJar(), getJDomJar());
    assertClassRoots(dependencies(myModule).withoutSdk().recursivelyExportedOnly(), getAsmJar());
    assertClassRoots(dependencies(myModule).withoutSdk().exportedOnly().recursively());
  }

  public void testAnnotations() {
    JpsLibrary library = addLibrary();
    String libraryUrl = "temp:///library";
    library.addRoot(libraryUrl, JpsAnnotationRootType.INSTANCE);
    JpsModuleRootModificationUtil.addDependency(myModule, library);
    assertEnumeratorRoots(dependencies(myModule).annotations(), libraryUrl);

    String moduleUrl = "temp://module";
    JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(myModule).getAnnotationRoots().addUrl(moduleUrl);
    assertEnumeratorRoots(dependencies(myModule).annotations(), moduleUrl, libraryUrl);
  }

  public void testJdkIsNotExported() throws Exception {
    assertClassRoots(dependencies(myModule).exportedOnly());
  }

  public void testProject() throws Exception {
    JpsModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());

    final String srcRoot = addSourceRoot(myModule, false);
    final String testRoot = addSourceRoot(myModule, true);
    final String output = setModuleOutput(myModule, false);
    final String testOutput = setModuleOutput(myModule, true);

    assertClassRoots(dependencies(myProject).withoutSdk(), testOutput, output, getJDomJar());
    assertSourceRoots(dependencies(myProject).withoutSdk(), srcRoot, testRoot, getJDomSources());
  }

  public void testModules() throws Exception {
    JpsModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());

    final String srcRoot = addSourceRoot(myModule, false);
    final String testRoot = addSourceRoot(myModule, true);
    final String output = setModuleOutput(myModule, false);
    final String testOutput = setModuleOutput(myModule, true);

    assertClassRoots(getJavaService().enumerateDependencies(Arrays.asList(myModule)).withoutSdk(),
                     testOutput, output, getJDomJar());
    assertSourceRoots(getJavaService().enumerateDependencies(Arrays.asList(myModule)).withoutSdk(),
                      srcRoot, testRoot, getJDomSources());
  }

  private String setModuleOutput(JpsModule module, boolean tests) {
    try {
      File file = FileUtil.createTempDirectory(module.getName(), tests ? "testSrc" : "src");
      JpsJavaModuleExtension extension = getJavaService().getOrCreateModuleExtension(module);
      String url = JpsPathUtil.getLibraryRootUrl(file);
      if (tests) {
        extension.setTestOutputUrl(url);
      }
      else {
        extension.setOutputUrl(url);
      }
      return url;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String addSourceRoot(JpsModule module, boolean tests) {
    try {
      File file = FileUtil.createTempDirectory(module.getName(), tests ? "testSrc" : "src");
      return module.addSourceRoot(JpsPathUtil.getLibraryRootUrl(file), tests ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE).getUrl();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void assertClassRoots(final JpsJavaDependenciesEnumerator enumerator, String... urls) {
    assertEnumeratorRoots(enumerator.classes(), urls);
  }

  private static void assertSourceRoots(final JpsJavaDependenciesEnumerator enumerator, String... urls) {
    assertEnumeratorRoots(enumerator.sources(), urls);
  }

  private static void assertEnumeratorRoots(JpsJavaDependenciesRootsEnumerator rootsEnumerator, String... urls) {
    assertOrderedEquals(rootsEnumerator.getUrls(), urls);
  }
}
