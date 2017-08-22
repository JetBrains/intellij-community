/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.compiler.artifacts;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.VfsTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class PackagingElementsTestCase extends ArtifactsTestCase {
  protected Artifact addArtifact(TestPackagingElementBuilder builder) {
    return addArtifact("a", builder);
  }

  protected Artifact addArtifact(final String name, TestPackagingElementBuilder builder) {
    return addArtifact(name, builder.build());
  }

  protected static void assertLayout(Artifact artifact, String expected) {
    assertLayout(artifact.getRootElement(), expected);
  }

  protected static void assertLayout(PackagingElement element, String expected) {
    ArtifactsTestUtil.assertLayout(element, expected);
  }

  protected String getProjectBasePath() {
    return getBaseDir().getPath();
  }

  protected VirtualFile getBaseDir() {
    final VirtualFile baseDir = myProject.getBaseDir();
    assertNotNull(baseDir);
    return baseDir;
  }

  protected TestPackagingElementBuilder root() {
    return TestPackagingElementBuilder.root(myProject);
  }

  protected TestPackagingElementBuilder archive(String name) {
    return TestPackagingElementBuilder.archive(myProject, name);
  }

  protected VirtualFile createFile(final String path) {
    return createFile(path, "");
  }

  protected VirtualFile createFile(final String path, final String text) {
    return VfsTestUtil.createFile(getBaseDir(), path, text);
  }

  protected VirtualFile createDir(final String path) {
    return VfsTestUtil.createDir(getBaseDir(), path);
  }

  protected static VirtualFile getJDomJar() {
    return IntelliJProjectConfiguration.getJarFromSingleJarProjectLibrary("JDOM");
  }

  protected static String getLocalJarPath(VirtualFile jarEntry) {
    return VfsUtil.getLocalFile(jarEntry).getPath();
  }

  protected Library addProjectLibrary(final @Nullable Module module, final String name, final VirtualFile... jars) {
    return addProjectLibrary(module, name, DependencyScope.COMPILE, jars);
  }

  protected Library addProjectLibrary(final @Nullable Module module, final String name, final DependencyScope scope,
                                      final VirtualFile... jars) {
    return addProjectLibrary(myProject, module, name, scope, jars);
  }

  static Library addProjectLibrary(final Project project, final @Nullable Module module, final String name, final DependencyScope scope,
                                   final VirtualFile[] jars) {
    return new WriteAction<Library>() {
      @Override
      protected void run(@NotNull final Result<Library> result) {
        final Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(project).createLibrary(name);
        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        for (VirtualFile jar : jars) {
          libraryModel.addRoot(jar, OrderRootType.CLASSES);
        }
        libraryModel.commit();
        if (module != null) {
          ModuleRootModificationUtil.addDependency(module, library, scope, false);
        }
        result.setResult(library);
      }
    }.execute().getResultObject();
  }

  protected static void addModuleLibrary(final Module module, final VirtualFile jar) {
    ModuleRootModificationUtil.addModuleLibrary(module, jar.getUrl());
  }

  protected static void addModuleDependency(final Module module, final Module dependency) {
    ModuleRootModificationUtil.addDependency(module, dependency);
  }
}
