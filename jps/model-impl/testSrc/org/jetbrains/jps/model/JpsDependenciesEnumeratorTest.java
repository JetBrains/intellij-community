// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.project.IntelliJProjectConfiguration;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsAnnotationRootType;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaDependenciesRootsEnumerator;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.IOException;
import java.util.Collections;

import static org.jetbrains.jps.model.java.JpsJavaExtensionService.dependencies;

public class JpsDependenciesEnumeratorTest extends JpsJavaModelTestCase {
  private JpsModule myModule;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    var jdk = addJdk("1.7");
    myModule = addModule();
    JpsModuleRootModificationUtil.setModuleSdk(myModule, jdk.getProperties());
  }

  private JpsTypedLibrary<JpsSdk<JpsDummyElement>> addJdk(String mockJdkVersion) {
    var mockJdkDir = "mockJDK-" + mockJdkVersion;
    var home = PathManagerEx.findFileUnderCommunityHome("java/" + mockJdkDir);
    var jdk = myModel.getGlobal().addSdk(mockJdkVersion, home.getAbsolutePath(), mockJdkVersion, JpsJavaSdkType.INSTANCE);
    jdk.addRoot(getRtJar(mockJdkDir), JpsOrderRootType.COMPILED);
    return jdk;
  }

  public void testLibrary() {
    JpsModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());

    assertClassRoots(dependencies(myModule), getRtJarJdk17(), getFastUtilJar());
    assertClassRoots(dependencies(myModule).withoutSdk(), getFastUtilJar());
    assertClassRoots(dependencies(myModule).withoutSdk().productionOnly().runtimeOnly(), getFastUtilJar());
    assertClassRoots(dependencies(myModule).withoutLibraries(), getRtJarJdk17());
    assertSourceRoots(dependencies(myModule), getFastUtilSources());
  }

  private static String getFastUtilSources() {
    return IntelliJProjectConfiguration.getProjectLibrary("fastutil-min").getSourcesUrls().getFirst();
  }

  private static String getFastUtilJar() {
    return getJarUrlFromProjectLib("fastutil-min");
  }

  private static String getSampleLibJar() {
    return getJarUrlFromProjectLib("byte-buddy");
  }

  private static String getJarUrlFromProjectLib(String libraryName) {
    return assertOneElement(IntelliJProjectConfiguration.getProjectLibraryClassesRootUrls(libraryName));
  }

  private static String getRtJarJdk17() {
    return getRtJar("mockJDK-1.7");
  }

  private static String getRtJarJdk18() {
    return getRtJar("mockJDK-1.8");
  }

  private static String getRtJar(String mockJdkDir) {
    return JpsPathUtil.getLibraryRootUrl(PathManagerEx.findFileUnderCommunityHome("java/" + mockJdkDir + "/jre/lib/rt.jar"));
  }

  private JpsLibrary createJDomLibrary() {
    var library = addLibrary("jdom");
    library.addRoot(getFastUtilJar(), JpsOrderRootType.COMPILED);
    library.addRoot(getFastUtilSources(), JpsOrderRootType.SOURCES);
    return library;
  }

  private JpsLibrary createSampleLibrary() {
    var library = addLibrary("sample");
    library.addRoot(getSampleLibJar(), JpsOrderRootType.COMPILED);
    return library;
  }

  public void testModuleSources() {
    var srcRoot = addSourceRoot(myModule, false);
    var testRoot = addSourceRoot(myModule, true);
    var output = setModuleOutput(myModule, false);
    var testOutput = setModuleOutput(myModule, true);

    assertClassRoots(dependencies(myModule).withoutSdk(), testOutput, output);
    assertClassRoots(dependencies(myModule).withoutSdk().productionOnly(), output);
    assertSourceRoots(dependencies(myModule), srcRoot, testRoot);
    assertSourceRoots(dependencies(myModule).productionOnly(), srcRoot);

    assertEnumeratorRoots(dependencies(myModule).withoutSdk().classes().withoutSelfModuleOutput(), output);
    assertEnumeratorRoots(dependencies(myModule).withoutSdk().productionOnly().classes().withoutSelfModuleOutput());
  }

  public void testLibraryScope() {
    var dependency = myModule.getDependenciesList().addLibraryDependency(createJDomLibrary());
    getJavaService().getOrCreateDependencyExtension(dependency).setScope(JpsJavaDependencyScope.RUNTIME);
    JpsModuleRootModificationUtil.addDependency(myModule, createJDomLibrary(), JpsJavaDependencyScope.RUNTIME, false);

    assertClassRoots(dependencies(myModule).withoutSdk(), getFastUtilJar());
    assertClassRoots(dependencies(myModule).withoutSdk().exportedOnly());
    assertClassRoots(dependencies(myModule).withoutSdk().compileOnly());
  }

  public void testModuleDependency() {
    var dep = addModule("dep");
    var depSrcRoot = addSourceRoot(dep, false);
    var depTestRoot = addSourceRoot(dep, true);
    var depOutput = setModuleOutput(dep, false);
    var depTestOutput = setModuleOutput(dep, true);
    JpsModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), JpsJavaDependencyScope.COMPILE, true);
    JpsModuleRootModificationUtil.addDependency(myModule, dep, JpsJavaDependencyScope.COMPILE, true);

    var srcRoot = addSourceRoot(myModule, false);
    var testRoot = addSourceRoot(myModule, true);
    var output = setModuleOutput(myModule, false);
    var testOutput = setModuleOutput(myModule, true);

    assertClassRoots(dependencies(myModule).withoutSdk(), testOutput, output, depTestOutput, depOutput);
    assertClassRoots(dependencies(myModule).withoutSdk().recursively(), testOutput, output, depTestOutput, depOutput, getFastUtilJar());
    assertSourceRoots(dependencies(myModule), srcRoot, testRoot, depSrcRoot, depTestRoot);
    assertSourceRoots(dependencies(myModule).recursively(), srcRoot, testRoot, depSrcRoot, depTestRoot, getFastUtilSources());

    assertClassRoots(dependencies(myModule).withoutSdk().withoutModuleSourceEntries().recursively(), getFastUtilJar());
    assertSourceRoots(dependencies(myModule).withoutSdk().withoutModuleSourceEntries().recursively(), getFastUtilSources());
    assertEnumeratorRoots(dependencies(myModule).withoutSdk().withoutModuleSourceEntries().recursively().classes(), getFastUtilJar());
    assertEnumeratorRoots(dependencies(myModule).withoutSdk().withoutModuleSourceEntries().recursively().sources(), getFastUtilSources());

    assertEnumeratorRoots(
      dependencies(myModule).withoutSdk().recursively().classes().withoutSelfModuleOutput(),
      output, depTestOutput, depOutput, getFastUtilJar()
    );
    assertEnumeratorRoots(
      dependencies(myModule).productionOnly().withoutSdk().recursively().classes().withoutSelfModuleOutput(),
      depOutput, getFastUtilJar()
    );

    assertClassRoots(dependencies(myModule).withoutSdk().withoutDepModules().withoutModuleSourceEntries().recursively(), getFastUtilJar());
    assertEnumeratorRoots(
      dependencies(myModule).productionOnly().withoutSdk().withoutDepModules().withoutModuleSourceEntries().recursively().classes(),
      getFastUtilJar()
    );
    assertClassRoots(dependencies(myModule).withoutSdk().withoutDepModules().withoutModuleSourceEntries());
    assertEnumeratorRoots(dependencies(myModule).productionOnly().withoutModuleSourceEntries().withoutSdk().withoutDepModules().classes());
  }

  public void testModuleJpsJavaDependencyScope() {
    var dep = addModule("dep");
    JpsModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), JpsJavaDependencyScope.COMPILE, true);
    JpsModuleRootModificationUtil.addDependency(myModule, dep, JpsJavaDependencyScope.TEST, true);

    assertClassRoots(dependencies(myModule).withoutSdk());
    assertClassRoots(dependencies(myModule).withoutSdk().recursively(), getFastUtilJar());
    assertClassRoots(dependencies(myModule).withoutSdk().exportedOnly().recursively(), getFastUtilJar());
    assertClassRoots(dependencies(myModule).withoutSdk().productionOnly().recursively());

    assertClassRoots(dependencies(myProject).withoutSdk(), getFastUtilJar());
    assertClassRoots(dependencies(myProject).withoutSdk().productionOnly(), getFastUtilJar());
  }

  public void testNotExportedLibrary() {
    var dep = addModule("dep");
    JpsModuleRootModificationUtil.addDependency(dep, createJDomLibrary(), JpsJavaDependencyScope.COMPILE, false);
    JpsModuleRootModificationUtil.addDependency(myModule, createSampleLibrary(), JpsJavaDependencyScope.COMPILE, false);
    JpsModuleRootModificationUtil.addDependency(myModule, dep, JpsJavaDependencyScope.COMPILE, false);

    assertClassRoots(dependencies(myModule).withoutSdk(), getSampleLibJar());
    assertClassRoots(dependencies(myModule).withoutSdk().recursively(), getSampleLibJar(), getFastUtilJar());
    assertClassRoots(dependencies(myModule).withoutSdk().recursivelyExportedOnly(), getSampleLibJar());
    assertClassRoots(dependencies(myModule).withoutSdk().exportedOnly().recursively());
  }

  public void testAnnotations() {
    var library = addLibrary();
    var libraryUrl = "temp:///library";
    library.addRoot(libraryUrl, JpsAnnotationRootType.INSTANCE);
    JpsModuleRootModificationUtil.addDependency(myModule, library);
    assertEnumeratorRoots(dependencies(myModule).annotations(), libraryUrl);

    var moduleUrl = "temp://module";
    JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(myModule).getAnnotationRoots().addUrl(moduleUrl);
    assertEnumeratorRoots(dependencies(myModule).annotations(), moduleUrl, libraryUrl);
  }

  public void testJdkIsNotExported() {
    assertClassRoots(dependencies(myModule).exportedOnly());
  }

  public void testDoNotAddJdkRootsFromModuleDependency() {
    var dep = addModule("dep");
    var jdk8 = addJdk("1.8");
    JpsModuleRootModificationUtil.addDependency(myModule, dep);
    JpsModuleRootModificationUtil.setModuleSdk(dep, jdk8.getProperties());
    assertClassRoots(dependencies(myModule).recursively(), getRtJarJdk17());
    assertClassRoots(dependencies(dep).recursively(), getRtJarJdk18());
  }

  public void testProject() {
    JpsModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());

    var srcRoot = addSourceRoot(myModule, false);
    var testRoot = addSourceRoot(myModule, true);
    var output = setModuleOutput(myModule, false);
    var testOutput = setModuleOutput(myModule, true);

    assertClassRoots(dependencies(myProject).withoutSdk(), testOutput, output, getFastUtilJar());
    assertSourceRoots(dependencies(myProject).withoutSdk(), srcRoot, testRoot, getFastUtilSources());
  }

  public void testModules() {
    JpsModuleRootModificationUtil.addDependency(myModule, createJDomLibrary());

    var srcRoot = addSourceRoot(myModule, false);
    var testRoot = addSourceRoot(myModule, true);
    var output = setModuleOutput(myModule, false);
    var testOutput = setModuleOutput(myModule, true);

    assertClassRoots(
      getJavaService().enumerateDependencies(Collections.singletonList(myModule)).withoutSdk(),
      testOutput, output, getFastUtilJar()
    );
    assertSourceRoots(
      getJavaService().enumerateDependencies(Collections.singletonList(myModule)).withoutSdk(),
      srcRoot, testRoot, getFastUtilSources()
    );
  }

  public void testIncludeTestsFromDependentModules() throws IOException {
    var depModule = addModule("dep");
    var depTestRoot = addSourceRoot(depModule, true);
    JpsModuleRootModificationUtil.addDependency(myModule, depModule, JpsJavaDependencyScope.TEST, false);

    // default: add test roots from dependent modules
    assertSourceRoots(
      getJavaService().enumerateDependencies(Collections.singletonList(myModule)).withoutSdk(),
      depTestRoot
    );
    assertSourceRoots(
      getJavaService().enumerateDependencies(Collections.singletonList(myModule)).recursively().withoutSdk(),
      depTestRoot
    );

    try (var ignored = TestJpsDependenciesEnumerationHandler.Companion.addModule(myModule, false)) {
      // modified with a handler: do NOT add test roots from dependent modules
      assertSourceRoots(getJavaService().enumerateDependencies(Collections.singletonList(myModule)).withoutSdk());
      assertSourceRoots(getJavaService().enumerateDependencies(Collections.singletonList(myModule)).recursively().withoutSdk());
    }
  }

  private String setModuleOutput(JpsModule module, boolean tests) {
    try {
      var file = FileUtil.createTempDirectory(module.getName(), tests ? "testSrc" : "src");
      var extension = getJavaService().getOrCreateModuleExtension(module);
      var url = JpsPathUtil.getLibraryRootUrl(file);
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
      var file = FileUtil.createTempDirectory(module.getName(), tests ? "testSrc" : "src");
      return module.addSourceRoot(JpsPathUtil.getLibraryRootUrl(file), tests ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE).getUrl();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void assertClassRoots(JpsJavaDependenciesEnumerator enumerator, String... urls) {
    assertEnumeratorRoots(enumerator.classes(), urls);
  }

  private static void assertSourceRoots(JpsJavaDependenciesEnumerator enumerator, String... urls) {
    assertEnumeratorRoots(enumerator.sources(), urls);
  }

  private static void assertEnumeratorRoots(JpsJavaDependenciesRootsEnumerator rootsEnumerator, String... urls) {
    assertOrderedEquals(rootsEnumerator.getUrls(), urls);
  }
}
