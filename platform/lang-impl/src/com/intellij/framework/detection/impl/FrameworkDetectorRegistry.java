// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  public abstract FrameworkType findFrameworkType(@NotNull String typeId);

  @NotNull
  public abstract List<? extends FrameworkType> getFrameworkTypes();

  @Nullable
  public abstract FrameworkDetector getDetectorById(@NotNull String id);

  @NotNull
  public abstract Collection<String> getDetectorIds(@NotNull FileType fileType);

  @NotNull
  public abstract Collection<String> getAllDetectorIds();

  @NotNull
  public abstract MultiMap<FileType, Pair<ElementPattern<FileContent>, String>> getDetectorsMap();

  public abstract FileType @NotNull [] getAcceptedFileTypes();
}
