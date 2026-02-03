// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl;

import com.intellij.framework.FrameworkType;
import com.intellij.framework.detection.FrameworkDetector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class FrameworkDetectorRegistry {
  public static FrameworkDetectorRegistry getInstance() {
    return ApplicationManager.getApplication().getService(FrameworkDetectorRegistry.class);
  }

  public abstract @Nullable FrameworkType findFrameworkType(@NotNull String typeId);

  public abstract @NotNull List<? extends FrameworkType> getFrameworkTypes();

  public abstract @Nullable FrameworkDetector getDetectorById(@NotNull String id);

  public abstract @NotNull Collection<String> getDetectorIds(@NotNull FileType fileType);

  public abstract @NotNull Collection<String> getAllDetectorIds();

  public abstract @NotNull MultiMap<FileType, Pair<ElementPattern<FileContent>, String>> getDetectorsMap();

  public abstract FileType @NotNull [] getAcceptedFileTypes();
}
