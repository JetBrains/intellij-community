/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JarVersionDetectionUtil {
  @NonNls private static final String IMPLEMENTATION_VERSION = "Implementation-Version";

  private JarVersionDetectionUtil() {
  }

  @Nullable
  public static String detectJarVersion(@NotNull final String detectionClass, @NotNull Module module) {
    try {
      return detectJarVersion(getDetectionJar(detectionClass, module));
    }
    catch (IOException e) {
      return null;
    }
  }

  @Nullable
  public static String detectJarVersion(ZipFile zipFile) {
    if (zipFile == null) {
      return null;
    }
    try {
      final ZipEntry zipEntry = zipFile.getEntry(JarFile.MANIFEST_NAME);
      if (zipEntry == null) {
        return null;
      }
      final InputStream inputStream = zipFile.getInputStream(zipEntry);
      final Manifest manifest = new Manifest(inputStream);
      final Attributes attributes = manifest.getMainAttributes();
      return attributes.getValue(IMPLEMENTATION_VERSION);
    }
    catch (IOException e) {
      return null;
    }
  }

  @Nullable
  private static ZipFile getDetectionJar(final String detectionClass, Module module) throws IOException {
      for (OrderEntry library : ModuleRootManager.getInstance(module).getOrderEntries()) {
        if (library instanceof LibraryOrderEntry) {
          VirtualFile file = LibrariesHelper.getInstance().findJarByClass(((LibraryOrderEntry)library).getLibrary(), detectionClass);
          if (file != null && file.getFileSystem() instanceof JarFileSystem) {
            return JarFileSystem.getInstance().getJarFile(file);
          }
        }
      }
    return null;
  }
}

