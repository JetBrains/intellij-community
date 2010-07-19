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
package com.intellij.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.OrderedSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * @author Dmitry Avdeev
 */
public class JarClasspathHelper {

  public static OrderedSet<VirtualFile> patchFiles(OrderedSet<VirtualFile> files, Project project) {
    if (!Registry.is("jar.build")) {
      return files;
    }
    String path = getJarsPath(project);
    final OrderedSet<VirtualFile> result = new OrderedSet<VirtualFile>(TObjectHashingStrategy.CANONICAL);
    final Collection<VirtualFile> modulesOutputs =
      new HashSet<VirtualFile>(Arrays.asList(OrderEnumerator.orderEntries(project).withoutLibraries().withoutSdk().getClassesRoots()));
    for (VirtualFile file : files) {
      VirtualFile jar = getJarFile(path, file.getName());
      if (modulesOutputs.contains(file) && jar != null) {
        result.add(jar);
      }
      else {
        result.add(file);
      }
    }
    return result;
  }

  @Nullable
  public static VirtualFile getJarFile(String path, String moduleName) {
    VirtualFile jar = LocalFileSystem.getInstance().refreshAndFindFileByPath(path + "/" + moduleName + ".jar");
    System.out.println(jar);
    return jar;
  }

  @Nullable
  public static String getJarsPath(Project project) {
    CompilerProjectExtension extension = CompilerProjectExtension.getInstance(project);
    if (extension == null) {
      return null;
    }
    VirtualFile output = extension.getCompilerOutput();
    if (output == null) {
      return null;
    }
    return output.getPath() + "/jars";
  }
}
