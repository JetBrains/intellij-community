// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public final class JarVersionDetectionUtil {
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
  public static String detectJarVersion(@NotNull String detectionClass, @NotNull List<? extends VirtualFile> files) {
    VirtualFile jarRoot = LibrariesHelper.getInstance().findRootByClass(files, detectionClass);
    return jarRoot != null && jarRoot.getFileSystem() instanceof JarFileSystem ?
           getMainAttribute(jarRoot, Attributes.Name.IMPLEMENTATION_VERSION) : null;
  }

  @Nullable
  public static String getMainAttribute(@NotNull VirtualFile jarRoot, @NotNull Attributes.Name attribute) {
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