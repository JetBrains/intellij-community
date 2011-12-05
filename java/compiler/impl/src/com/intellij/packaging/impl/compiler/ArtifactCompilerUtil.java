/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.packaging.impl.compiler;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PackagingElementPath;
import com.intellij.packaging.impl.artifacts.PackagingElementProcessor;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;
import com.intellij.packaging.impl.elements.FileOrDirectoryCopyPackagingElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author nik
 */
public class ArtifactCompilerUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.packaging.impl.compiler.ArtifactCompilerUtil");

  private ArtifactCompilerUtil() {
  }

  @Nullable
  public static BufferedInputStream getJarEntryInputStream(VirtualFile sourceFile, final CompileContext context) throws IOException {
    final String fullPath = sourceFile.getPath();
    final int jarEnd = fullPath.indexOf(JarFileSystem.JAR_SEPARATOR);
    LOG.assertTrue(jarEnd != -1, fullPath);
    String pathInJar = fullPath.substring(jarEnd + JarFileSystem.JAR_SEPARATOR.length());
    String jarPath = fullPath.substring(0, jarEnd);
    final ZipFile jarFile = new ZipFile(new File(FileUtil.toSystemDependentName(jarPath)));
    final ZipEntry entry = jarFile.getEntry(pathInJar);
    if (entry == null) {
      context.addMessage(CompilerMessageCategory.ERROR, "Cannot extract '" + pathInJar + "' from '" + jarFile.getName() + "': entry not found", null, -1, -1);
      return null;
    }

    return new BufferedInputStream(jarFile.getInputStream(entry)) {
      @Override
      public void close() throws IOException {
        super.close();
        jarFile.close();
      }
    };
  }

  public static File getJarFile(VirtualFile jarEntry) {
    String fullPath = jarEntry.getPath();
    return new File(FileUtil.toSystemDependentName(fullPath.substring(fullPath.indexOf(JarFileSystem.JAR_SEPARATOR))));
  }


  @NotNull
  public static Set<VirtualFile> getArtifactOutputsContainingSourceFiles(final @NotNull Project project) {
    final List<VirtualFile> allOutputs = new ArrayList<VirtualFile>();
    for (Artifact artifact : ArtifactManager.getInstance(project).getArtifacts()) {
      ContainerUtil.addIfNotNull(artifact.getOutputFile(), allOutputs);
    }

    final Set<VirtualFile> affectedOutputPaths = new HashSet<VirtualFile>();
    final PackagingElementResolvingContext context = ArtifactManager.getInstance(project).getResolvingContext();
    for (Artifact artifact : ArtifactManager.getInstance(project).getArtifacts()) {
      ArtifactUtil.processPackagingElements(artifact, null, new PackagingElementProcessor<PackagingElement<?>>() {
        @Override
        public boolean shouldProcessSubstitution(ComplexPackagingElement<?> element) {
          return !(element instanceof ArtifactPackagingElement);
        }

        @Override
        public boolean process(@NotNull PackagingElement<?> element, @NotNull PackagingElementPath path) {
          if (element instanceof FileOrDirectoryCopyPackagingElement<?>) {
            final VirtualFile file = ((FileOrDirectoryCopyPackagingElement)element).findFile();
            if (file != null) {
              for (VirtualFile output : allOutputs) {
                if (VfsUtilCore.isAncestor(output, file, false)) {
                  affectedOutputPaths.add(output);
                }
              }
            }
          }
          return true;
        }
      }, context, true);
    }
    return affectedOutputPaths;
  }
}
