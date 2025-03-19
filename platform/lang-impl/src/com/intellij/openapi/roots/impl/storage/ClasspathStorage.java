// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl.storage;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

public final class ClasspathStorage {
  public static @Nullable ClasspathStorageProvider getProvider(@NotNull String type) {
    if (type.equals(ClassPathStorageUtil.DEFAULT_STORAGE)) {
      return null;
    }

    for (ClasspathStorageProvider provider : ClasspathStorageProvider.EXTENSION_POINT_NAME.getExtensions()) {
      if (type.equals(provider.getID())) {
        return provider;
      }
    }
    return null;
  }

  public static @NotNull String getStorageRootFromOptions(@NotNull Module module) {
    String moduleRoot = ModuleUtilCore.getModuleDirPath(module);
    String storageRef = module.getOptionValue(JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE);
    if (storageRef == null) {
      return moduleRoot;
    }

    storageRef = FileUtil.toSystemIndependentName(storageRef);
    if (SystemInfo.isWindows ? FileUtil.isAbsolutePlatformIndependent(storageRef) : FileUtil.isUnixAbsolutePath(storageRef)) {
      return storageRef;
    }
    else {
      return moduleRoot + '/' + storageRef;
    }
  }

  public static void setStorageType(@NotNull ModuleRootModel model, @NotNull String storageId) {
    Module module = model.getModule();
    String oldStorageType = ClassPathStorageUtil.getStorageType(module);
    if (oldStorageType.equals(storageId)) {
      return;
    }

    ClasspathStorageProvider provider = getProvider(oldStorageType);
    if (provider != null) {
      provider.detach(module);
    }

    ClasspathStorageProvider newProvider = getProvider(storageId);
    module.setOption(JpsProjectLoader.CLASSPATH_ATTRIBUTE, newProvider == null ? null : storageId);
    module.setOption(JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE, newProvider == null ? null : newProvider.getContentRoot(model));
    if (newProvider != null) {
      newProvider.attach(model);
    }
  }

  public static void modulePathChanged(@NotNull Module module) {
    ClasspathStorageProvider provider = getProvider(ClassPathStorageUtil.getStorageType(module));
    if (provider != null) {
      provider.modulePathChanged(module);
    }
  }
}
