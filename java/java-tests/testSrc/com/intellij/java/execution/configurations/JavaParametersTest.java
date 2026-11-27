// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.configurations;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.java.roots.ModuleRootManagerFixture;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.HeavyTestHelper;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.lang.JavaVersion;
import org.intellij.lang.annotations.MagicConstant;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.java.roots.ModuleRootManagerFixture.assertRoots;
import static com.intellij.java.roots.ModuleRootManagerFixture.getMockJdk17WithRtJarOnly;
import static com.intellij.java.roots.ModuleRootManagerFixture.getMockJdk18WithRtJarOnly;
import static com.intellij.java.roots.ModuleRootManagerFixture.getRtJarJdk17;
import static com.intellij.java.roots.ModuleRootManagerFixture.getRtJarJdk18;
import static com.intellij.java.roots.ModuleRootManagerFixture.moduleRootManagerFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaParametersTest {
  private final TestFixture<ModuleRootManagerFixture> heavyFixture = moduleRootManagerFixture();

  @Test
  public void testLibrary() throws Exception {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    var libraryJar = createJar("library.jar");
    ModuleRootModificationUtil.addDependency(fixture.getModule(), createLibraryWithSources(libraryJar));
    assertClasspath(fixture.getModule(), JavaParameters.JDK_AND_CLASSES_AND_TESTS, getRtJarJdk17(), libraryJar);
    assertClasspath(fixture.getModule(), JavaParameters.CLASSES_ONLY, libraryJar);
    assertClasspath(fixture.getModule(), JavaParameters.CLASSES_AND_TESTS, libraryJar);
    assertClasspath(fixture.getProject(), JavaParameters.JDK_AND_CLASSES_AND_TESTS, getRtJarJdk17(), libraryJar);
  }

  private Library createLibraryWithSources(VirtualFile classesRoot) {
    return heavyFixture.get().createLibrary("library", classesRoot, createJar("library-sources.jar"));
  }

  private VirtualFile createJar(String name) {
    var jar = IoTestUtil.createTestJar(new File(HeavyTestHelper.getOrCreateProjectBaseDir(heavyFixture.get().getProject()).toNioPath().toFile(), name));
    var jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar)));
    assertNotNull(jarRoot);
    return jarRoot;
  }

  @Test
  public void testModuleSourcesAndOutput() throws Exception {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    fixture.addSourceRoot(fixture.getModule(), false);
    fixture.addSourceRoot(fixture.getModule(), true);
    VirtualFile output = fixture.setModuleOutput(fixture.getModule(), false);
    VirtualFile testOutput = fixture.setModuleOutput(fixture.getModule(), true);

    assertClasspath(fixture.getModule(), JavaParameters.CLASSES_ONLY, output);
    assertClasspath(fixture.getModule(), JavaParameters.CLASSES_AND_TESTS, testOutput, output);
    assertClasspath(fixture.getModule(), JavaParameters.JDK_AND_CLASSES_AND_TESTS, getRtJarJdk17(), testOutput, output);
  }

  @Test
  public void testLibraryScope() throws Exception {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    var libraryJar = createJar("library.jar");
    var testLibraryJar = createJar("test-library.jar");
    ModuleRootModificationUtil.addDependency(fixture.getModule(), createLibraryWithSources(libraryJar), DependencyScope.RUNTIME, false);
    ModuleRootModificationUtil.addDependency(fixture.getModule(), fixture.createLibrary("test-library", testLibraryJar, null), DependencyScope.TEST, false);

    assertClasspath(fixture.getModule(), JavaParameters.CLASSES_AND_TESTS, libraryJar, testLibraryJar);
    assertClasspath(fixture.getModule(), JavaParameters.CLASSES_ONLY, libraryJar);
  }

  @Test
  public void testProvidedScope() throws Exception {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    var libraryJar = createJar("library.jar");
    var testLibraryJar = createJar("test-library.jar");
    ModuleRootModificationUtil.addDependency(fixture.getModule(), createLibraryWithSources(libraryJar), DependencyScope.PROVIDED, false);
    ModuleRootModificationUtil.addDependency(fixture.getModule(), fixture.createLibrary("test-library", testLibraryJar, null), DependencyScope.TEST, false);

    assertClasspath(fixture.getModule(), JavaParameters.CLASSES_AND_TESTS, libraryJar, testLibraryJar);
    assertClasspath(fixture.getModule(), JavaParameters.CLASSES_ONLY);
    assertClasspath(fixture.getModule(), JavaParameters.JDK_AND_CLASSES_AND_PROVIDED, getRtJarJdk17(), libraryJar);
  }

  @Test
  public void testModuleDependency() throws Exception {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    var libraryJar = createJar("library.jar");
    final Module dep = fixture.createModule("dep");
    final VirtualFile depOutput = fixture.setModuleOutput(dep, false);
    final VirtualFile depTestOutput = fixture.setModuleOutput(dep, true);
    ModuleRootModificationUtil.addDependency(dep, createLibraryWithSources(libraryJar));
    ModuleRootModificationUtil.addDependency(fixture.getModule(), dep, DependencyScope.COMPILE, false);

    assertClasspath(fixture.getModule(), JavaParameters.CLASSES_ONLY,
                    depOutput, libraryJar);
    assertClasspath(fixture.getModule(), JavaParameters.CLASSES_AND_TESTS,
                    depTestOutput, depOutput, libraryJar);
  }

  @Test
  public void testModuleDependencyScope() throws Exception {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    var libraryJar = createJar("library.jar");
    final Module dep = fixture.createModule("dep");
    ModuleRootModificationUtil.addDependency(dep, createLibraryWithSources(libraryJar));
    ModuleRootModificationUtil.addDependency(fixture.getModule(), dep, DependencyScope.TEST, true);

    assertClasspath(fixture.getModule(), JavaParameters.CLASSES_ONLY);
    assertClasspath(fixture.getModule(), JavaParameters.CLASSES_AND_TESTS,
                    libraryJar);

    assertClasspath(fixture.getProject(), JavaParameters.CLASSES_ONLY,
                    libraryJar);
  }

  @Test
  public void testUseNewestJreVersion() throws CantRunException {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    Module dep = fixture.createModule("dep");
    ModuleRootModificationUtil.addDependency(fixture.getModule(), dep, DependencyScope.TEST, true);
    ModuleRootModificationUtil.setModuleSdk(fixture.getModule(), getMockJdk17WithRtJarOnly());
    Sdk sdk = getMockJdk18WithRtJarOnly();
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(sdk, fixture.getTestRootDisposable()));
    ModuleRootModificationUtil.setModuleSdk(dep, sdk);
    assertClasspath(fixture.getModule(), JavaParameters.JDK_AND_CLASSES, getRtJarJdk17());
    assertClasspath(fixture.getModule(), JavaParameters.JDK_AND_CLASSES_AND_TESTS, getRtJarJdk18());
  }

  private static void assertClasspath(final Module module, @MagicConstant(flagsFromClass = JavaParameters.class) int type, VirtualFile... roots) throws CantRunException {
    final JavaParameters javaParameters = new JavaParameters();
    javaParameters.configureByModule(module, type);
    assertRoots(javaParameters.getClassPath(), roots);
  }

  private void assertClasspath(final Project project, @MagicConstant(flagsFromClass = JavaParameters.class) int type, VirtualFile... roots) throws CantRunException {
    final JavaParameters javaParameters = new JavaParameters();
    javaParameters.configureByProject(project, type, getMockJdk17WithRtJarOnly());
    assertRoots(javaParameters.getClassPath(), roots);
  }

  @Test
  public void testPreviewLanguageFeatures() throws CantRunException {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    ModuleRootModificationUtil.updateModel(fixture.getModule(), (model) -> {
      model.getModuleExtension(LanguageLevelModuleExtension.class)
           .setLanguageLevel(LanguageLevel.JDK_21_PREVIEW);
      Sdk mockJdk = IdeaTestUtil.getMockJdk(JavaVersion.compose(14));
      WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(mockJdk, fixture.getProject()));
      model.setSdk(mockJdk);
    });
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.configureByModule(fixture.getModule(), JavaParameters.CLASSES_AND_TESTS);
    assertTrue(javaParameters.getVMParametersList().hasParameter(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY));

    ModuleRootModificationUtil.updateModel(fixture.getModule(), (model) -> model.getModuleExtension(LanguageLevelModuleExtension.class)
                                                                     .setLanguageLevel(LanguageLevel.JDK_15));
    javaParameters = new JavaParameters();
    javaParameters.configureByModule(fixture.getModule(), JavaParameters.CLASSES_AND_TESTS);
    assertFalse(javaParameters.getVMParametersList().hasParameter(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY));
  }

  /**
   * A dependency at a preview language level needs the flag, and one flag is enough. The enumeration visits the whole
   * graph, so a plain dependency after a preview one must neither hide the flag nor add it again.
   */
  @Test
  public void testPreviewLanguageFeaturesInDependencies() throws CantRunException {
    ModuleRootManagerFixture fixture = heavyFixture.get();
    Sdk mockJdk = IdeaTestUtil.getMockJdk(JavaVersion.compose(14));
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(mockJdk, fixture.getProject()));
    ModuleRootModificationUtil.updateModel(fixture.getModule(), (model) -> model.setSdk(mockJdk));

    Module preview = fixture.createModule("preview");
    ModuleRootModificationUtil.updateModel(preview, (model) -> model.getModuleExtension(LanguageLevelModuleExtension.class)
                                                                    .setLanguageLevel(LanguageLevel.JDK_21_PREVIEW));
    Module alsoPreview = fixture.createModule("alsoPreview");
    ModuleRootModificationUtil.updateModel(alsoPreview, (model) -> model.getModuleExtension(LanguageLevelModuleExtension.class)
                                                                        .setLanguageLevel(LanguageLevel.JDK_21_PREVIEW));
    Module plain = fixture.createModule("plain");
    ModuleRootModificationUtil.updateModel(plain, (model) -> model.getModuleExtension(LanguageLevelModuleExtension.class)
                                                                  .setLanguageLevel(LanguageLevel.JDK_15));

    ModuleRootModificationUtil.addDependency(fixture.getModule(), preview, DependencyScope.COMPILE, false);
    ModuleRootModificationUtil.addDependency(fixture.getModule(), plain, DependencyScope.COMPILE, false);
    ModuleRootModificationUtil.addDependency(fixture.getModule(), alsoPreview, DependencyScope.COMPILE, false);

    JavaParameters javaParameters = new JavaParameters();
    javaParameters.configureByModule(fixture.getModule(), JavaParameters.CLASSES_AND_TESTS);
    List<String> vmParameters = javaParameters.getVMParametersList().getList();
    assertEquals(1, Collections.frequency(vmParameters, JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY), vmParameters.toString());
  }
}
