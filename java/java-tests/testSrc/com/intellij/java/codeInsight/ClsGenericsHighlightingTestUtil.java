// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaProjectBuilderCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The fixture and the helpers of the highlighting tests of compiled library classes.
 */
final class ClsGenericsHighlightingTestUtil {
  private ClsGenericsHighlightingTestUtil() {
  }

  /**
   * Creates the fixture of one test with a new heavy project. Declare it as an instance field.
   * The project has one Java module with {@code languageLevel} and the mock JDK 1.8. The module has no content root.
   * The test data is in {@code codeInsight/clsHighlighting}.
   */
  static @NotNull TestFixture<JavaCodeInsightTestFixture> clsGenericsHighlightingFixture(@NotNull LanguageLevel languageLevel) {
    return javaProjectBuilderCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/clsHighlighting", (projectBuilder, ignored) -> {
      JavaModuleFixtureBuilder<?> builder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
      builder.setLanguageLevel(languageLevel);
      builder.addJdkVersion(LanguageLevel.JDK_1_8);
    });
  }

  /**
   * Adds the library {@code name.jar}, configures {@code name.java} and checks its highlighting.
   */
  static void doTest(@NotNull JavaCodeInsightTestFixture fixture, @NotNull String name) {
    addLibrary(fixture, name + ".jar");
    fixture.configureByFile(name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> fixture.checkHighlighting());
  }

  /**
   * Adds a module library with the jar files of {@code libs} in the test data, and makes the temp directory a source content root.
   */
  static void addLibrary(@NotNull JavaCodeInsightTestFixture fixture, final String @NotNull ... libraryPath) {
    ModuleRootModificationUtil.updateModel(fixture.getModule(), model -> {
      commitLibraryModel(model, fixture.getTestDataPath(), libraryPath);

      String contentUrl = VfsUtilCore.pathToUrl(fixture.getTempDirPath());
      model.addContentEntry(contentUrl).addSourceFolder(contentUrl, false);
    });
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.getProject());
  }

  /**
   * Adds the module library {@code test} with the jar files of {@code libs} in {@code testDataPath} to {@code model}.
   * A jar file with {@code -sources} in its name gives the sources.
   */
  static void commitLibraryModel(ModifiableRootModel model, String testDataPath, String @NotNull ... libraryPath) {
    LibraryTable libraryTable = model.getModuleLibraryTable();
    Library library = libraryTable.createLibrary("test");

    Library.ModifiableModel libraryModel = library.getModifiableModel();
    for (String annotationsDir : libraryPath) {
      String path = testDataPath + "/libs/" + annotationsDir;
      VirtualFile libJarLocal = StandardFileSystems.local().findFileByPath(path);
      assertNotNull(libJarLocal);
      VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(libJarLocal);
      assertNotNull(jarRoot);
      libraryModel.addRoot(jarRoot, jarRoot.getName().contains("-sources") ? OrderRootType.SOURCES 
                                                                           : OrderRootType.CLASSES);
    }
    libraryModel.commit();
  }
}
