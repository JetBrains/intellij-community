// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFileBasedSubFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A heavy Java project for the module root tests: a new file-based project for each test,
 * with one main module and the mock JDK 1.7 that has only {@code rt.jar} in its classes.
 * <p>
 * Create it with {@link #moduleRootManagerFixture()}.
 */
@TestOnly
public final class ModuleRootManagerFixture extends HeavyJavaCodeInsightFixture {
  private ModuleRootManagerFixture(@NotNull Context context) {
    super(context, JavaTestUtil.getJavaTestDataPath());
  }

  /**
   * Creates a {@link ModuleRootManagerFixture} with a new project for each test. Declare it as an instance field.
   */
  public static @NotNull TestFixture<ModuleRootManagerFixture> moduleRootManagerFixture() {
    return heavyJavaCodeInsightFileBasedSubFixture("moduleRootManagerFixture", context -> new ModuleRootManagerFixture(context));
  }

  @Override
  protected @NotNull Sdk getTestProjectJdk() {
    return getMockJdk17WithRtJarOnly();
  }

  /**
   * Checks that the root directories of {@code pathsList} are {@code files}, in this order.
   */
  public static void assertRoots(PathsList pathsList, VirtualFile... files) {
    assertThat(pathsList.getRootDirs()).containsExactly(files);
  }

  public static @NotNull Sdk getMockJdk17WithRtJarOnly() {
    return retainRtJarOnlyAndSetVersion(IdeaTestUtil.getMockJdk17());
  }

  public static @NotNull Sdk getMockJdk18WithRtJarOnly() {
    return retainRtJarOnlyAndSetVersion(IdeaTestUtil.getMockJdk18());
  }

  @Contract(pure = true)
  private static @NotNull Sdk retainRtJarOnlyAndSetVersion(Sdk jdk) {
    try {
      jdk = jdk.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
    final SdkModificator modificator = jdk.getSdkModificator();
    VirtualFile rtJar = null;
    for (VirtualFile root : modificator.getRoots(OrderRootType.CLASSES)) {
      if (root.getName().equals("rt.jar")) {
        rtJar = root;
        break;
      }
    }
    assertNotNull(rtJar, "rt.jar not found in jdk: " + jdk);
    modificator.setVersionString(IdeaTestUtil.getMockJdkVersion(jdk.getHomePath()));
    modificator.removeAllRoots();
    modificator.addRoot(rtJar, OrderRootType.CLASSES);
    WriteAction.runAndWait(() -> modificator.commitChanges());
    return jdk;
  }

  public static VirtualFile getRtJarJdk17() {
    return getMockJdk17WithRtJarOnly().getRootProvider().getFiles(OrderRootType.CLASSES)[0];
  }

  public static VirtualFile getRtJarJdk18() {
    return getMockJdk18WithRtJarOnly().getRootProvider().getFiles(OrderRootType.CLASSES)[0];
  }

  /**
   * Adds a new temporary directory as a content root and a source root of {@code module}, and returns it.
   */
  public VirtualFile addSourceRoot(Module module, boolean testSource) {
    final VirtualFile root = createTempVirtualDir();
    PsiTestUtil.addSourceContentToRoots(module, root, testSource);
    return root;
  }

  /**
   * Makes a new temporary directory the production or the test output of {@code module}, and returns it.
   */
  public VirtualFile setModuleOutput(final Module module, final boolean test) {
    final VirtualFile output = createTempVirtualDir();
    PsiTestUtil.setCompilerOutputPath(module, output.getUrl(), test);
    return output;
  }

  /**
   * Creates the project library {@code name} with the classes root and the sources root.
   */
  public Library createLibrary(final String name, final @Nullable VirtualFile classesRoot, final @Nullable VirtualFile sourceRoot) {
    return WriteAction.computeAndWait(() -> {
      final Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(getProject()).createLibrary(name);
      final Library.ModifiableModel model = library.getModifiableModel();
      if (classesRoot != null) {
        model.addRoot(classesRoot, OrderRootType.CLASSES);
      }
      if (sourceRoot != null) {
        model.addRoot(sourceRoot, OrderRootType.SOURCES);
      }
      model.commit();
      return library;
    });
  }
}
