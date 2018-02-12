/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.roots;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author nik
 */
public abstract class ModuleRootManagerTestCase extends ModuleTestCase {
  protected static void assertRoots(PathsList pathsList, VirtualFile... files) {
    assertOrderedEquals(pathsList.getRootDirs(), files);
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return getMockJdk17WithRtJarOnly();
  }

  @NotNull
  protected static Sdk getMockJdk17WithRtJarOnly() {
    return retainRtJarOnlyAndSetVersion(IdeaTestUtil.getMockJdk17());
  }

  protected Sdk getMockJdk18WithRtJarOnly() {
    return retainRtJarOnlyAndSetVersion(IdeaTestUtil.getMockJdk18());
  }

  @NotNull
  @Contract(pure = true)
  private static Sdk retainRtJarOnlyAndSetVersion(Sdk jdk) {
    try {
      jdk = (Sdk)jdk.clone();
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
    modificator.commitChanges();
    return jdk;
  }

  protected VirtualFile getRtJarJdk17() {
    return getMockJdk17WithRtJarOnly().getRootProvider().getFiles(OrderRootType.CLASSES)[0];
  }

  protected VirtualFile getRtJarJdk18() {
    return getMockJdk18WithRtJarOnly().getRootProvider().getFiles(OrderRootType.CLASSES)[0];
  }

  protected VirtualFile getJDomJar() {
    return IntelliJProjectConfiguration.getJarFromSingleJarProjectLibrary("JDOM");
  }

  protected VirtualFile getJDomSources() {
    String url = assertOneElement(IntelliJProjectConfiguration.getProjectLibrary("JDOM").getSourcesUrls());
    VirtualFile jar = VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
    assertNotNull(jar);
    return jar;
  }

  protected VirtualFile addSourceRoot(Module module, boolean testSource) throws IOException {
    final VirtualFile root = getVirtualFile(createTempDir(module.getName() + (testSource ? "Test" : "Prod") + "Src"));
    PsiTestUtil.addSourceContentToRoots(module, root, testSource);
    return root;
  }

  protected VirtualFile setModuleOutput(final Module module, final boolean test) throws IOException {
    final VirtualFile output = getVirtualFile(createTempDir(module.getName() + (test ? "Test" : "Prod") + "Output"));
    PsiTestUtil.setCompilerOutputPath(module, output != null ? output.getUrl() : null, test);
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

  protected Library createJDomLibrary() {
    return createLibrary("jdom", getJDomJar(), getJDomSources());
  }

  protected Library createAsmLibrary() {
    return createLibrary("asm", getAsmJar(), null);
  }

  protected VirtualFile getAsmJar() {
    return IntelliJProjectConfiguration.getJarFromSingleJarProjectLibrary("ASM");
  }
}
