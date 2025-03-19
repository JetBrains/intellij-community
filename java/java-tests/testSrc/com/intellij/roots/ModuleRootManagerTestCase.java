// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.JavaModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public abstract class ModuleRootManagerTestCase extends JavaModuleTestCase {
  protected static void assertRoots(PathsList pathsList, VirtualFile... files) {
    assertOrderedEquals(pathsList.getRootDirs(), files);
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return getMockJdk17WithRtJarOnly();
  }

  protected static @NotNull Sdk getMockJdk17WithRtJarOnly() {
    return retainRtJarOnlyAndSetVersion(IdeaTestUtil.getMockJdk17());
  }

  protected Sdk getMockJdk18WithRtJarOnly() {
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
    assertNotNull("rt.jar not found in jdk: " + jdk, rtJar);
    modificator.setVersionString(IdeaTestUtil.getMockJdkVersion(jdk.getHomePath()));
    modificator.removeAllRoots();
    modificator.addRoot(rtJar, OrderRootType.CLASSES);
    ApplicationManager.getApplication().runWriteAction(() -> modificator.commitChanges());
    return jdk;
  }

  protected VirtualFile getRtJarJdk17() {
    return getMockJdk17WithRtJarOnly().getRootProvider().getFiles(OrderRootType.CLASSES)[0];
  }

  protected VirtualFile getRtJarJdk18() {
    return getMockJdk18WithRtJarOnly().getRootProvider().getFiles(OrderRootType.CLASSES)[0];
  }

  protected static VirtualFile getFastUtilJar() {
    return IntelliJProjectConfiguration.getJarFromSingleJarProjectLibrary("fastutil-min");
  }

  protected static Path getLibSources() {
    return IntelliJProjectConfiguration.getJarPathFromSingleJarProjectLibrary("gson");
  }

  protected VirtualFile addSourceRoot(Module module, boolean testSource) throws IOException {
    final VirtualFile root = getVirtualFile(createTempDir(module.getName() + (testSource ? "Test" : "Prod") + "Src"));
    PsiTestUtil.addSourceContentToRoots(module, root, testSource);
    return root;
  }

  protected VirtualFile setModuleOutput(final Module module, final boolean test) throws IOException {
    final VirtualFile output = getVirtualFile(createTempDir(module.getName() + (test ? "Test" : "Prod") + "Output"));
    PsiTestUtil.setCompilerOutputPath(module, output.getUrl(), test);
    return output;
  }

  protected Library createLibrary(final String name, final @Nullable VirtualFile classesRoot, final @Nullable VirtualFile sourceRoot) {
    return WriteAction.compute(() -> {
      final Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).createLibrary(name);
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

  protected Library createAsmLibrary() {
    return createLibrary("asm", getAsmJar(), null);
  }

  protected VirtualFile getAsmJar() {
    return IntelliJProjectConfiguration.getJarFromSingleJarProjectLibrary("ASM");
  }
}
