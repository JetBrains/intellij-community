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
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class JarVersionDetectionUtil {
  private JarVersionDetectionUtil() { }

  @Nullable
  public static String detectJarVersion(@NotNull String detectionClass, @NotNull Module module) {
    for (OrderEntry library : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (library instanceof LibraryOrderEntry) {
        VirtualFile jar = LibrariesHelper.getInstance().findJarByClass(((LibraryOrderEntry)library).getLibrary(), detectionClass);
        if (jar != null && jar.getFileSystem() instanceof JarFileSystem) {
          return getMainAttribute(jar, Attributes.Name.IMPLEMENTATION_VERSION);
        }
      }
    }

    return null;
  }

  @Nullable
  public static String detectJarVersion(@NotNull String detectionClass, @NotNull List<VirtualFile> files) {
    VirtualFile jarRoot = LibrariesHelper.getInstance().findRootByClass(files, detectionClass);
    return jarRoot != null && jarRoot.getFileSystem() instanceof JarFileSystem ?
           getMainAttribute(jarRoot, Attributes.Name.IMPLEMENTATION_VERSION) : null;
  }

  private static String getMainAttribute(VirtualFile jarRoot, Attributes.Name attribute) {
    VirtualFile manifestFile = jarRoot.findFileByRelativePath(JarFile.MANIFEST_NAME);
    if (manifestFile != null) {
      try (InputStream stream = manifestFile.getInputStream()) {
        return new Manifest(stream).getMainAttributes().getValue(attribute);
      }
      catch (IOException e) {
        Logger.getInstance(JarVersionDetectionUtil.class).debug(e);
      }
    }

    return null;
  }

  @Nullable
  public static String getBundleVersion(@NotNull File jar) {
    return JarUtil.getJarAttribute(jar, new Attributes.Name("Bundle-Version"));
  }

  @Nullable
  public static String getImplementationVersion(@NotNull File jar) {
    return JarUtil.getJarAttribute(jar, Attributes.Name.IMPLEMENTATION_VERSION);
  }
}