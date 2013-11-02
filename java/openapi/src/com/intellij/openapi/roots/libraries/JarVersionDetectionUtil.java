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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.libraries.JarVersionDetectionUtil");

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
  public static String detectJarVersion(@NotNull String detectionClass, @NotNull List<VirtualFile> files) {
    final VirtualFile jar = LibrariesHelper.getInstance().findRootByClass(files, detectionClass);
    if (jar != null && jar.getFileSystem() instanceof JarFileSystem) {
      final VirtualFile manifestFile = jar.findFileByRelativePath(JarFile.MANIFEST_NAME);
      if (manifestFile != null) {
        try {
          final InputStream input = manifestFile.getInputStream();
          try {
            return new Manifest(input).getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
          }
          finally {
            input.close();
          }
        }
        catch (IOException e) {
          LOG.debug(e);
          return null;
        }
      }
    }
    return null;
  }

  @Nullable
  public static String detectJarVersion(com.intellij.openapi.vfs.JarFile zipFile) {
    if (zipFile == null) {
      return null;
    }
    try {
      final com.intellij.openapi.vfs.JarFile.JarEntry zipEntry = zipFile.getEntry(JarFile.MANIFEST_NAME);
      if (zipEntry == null) {
        return null;
      }
      final InputStream inputStream = zipFile.getInputStream(zipEntry);
      final Manifest manifest = new Manifest(inputStream);
      final Attributes attributes = manifest.getMainAttributes();
      return attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
    }
    catch (IOException e) {
      return null;
    }
  }

  @Nullable
  private static com.intellij.openapi.vfs.JarFile getDetectionJar(final String detectionClass, Module module) throws IOException {
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

  private static String getJarAttribute(@NotNull File jar, @NotNull String attributeName, @Nullable String entryName) throws IOException {
    JarFile runJar = new JarFile(jar);
    try {
      Attributes attributes = entryName == null ? runJar.getManifest().getMainAttributes() : runJar.getManifest().getAttributes(entryName);
      return attributes.getValue(attributeName);
    }
    finally {
      runJar.close();
    }
  }

  public static String getBundleVersion(@NotNull File jar) throws IOException {
    return getJarAttribute(jar, "Bundle-Version", null);
  }

  public static String getImplementationVersion(@NotNull File jar) throws IOException {
    return getJarAttributeVersion(jar, Attributes.Name.IMPLEMENTATION_VERSION, null);
  }

  public static String getJarAttributeVersion(@NotNull File jar, @NotNull Attributes.Name attributeName, @Nullable String entryName) throws IOException {
    return getJarAttribute(jar, attributeName.toString(), entryName);
  }
}

