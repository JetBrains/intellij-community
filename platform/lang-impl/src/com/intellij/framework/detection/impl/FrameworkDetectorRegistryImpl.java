// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl;

import com.intellij.framework.FrameworkType;
import com.intellij.framework.detection.FrameworkDetector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class FrameworkDetectorRegistryImpl extends FrameworkDetectorRegistry implements Disposable {
  private static final Logger LOG = Logger.getInstance(FrameworkDetectorRegistryImpl.class);

  private volatile Map<String, FrameworkDetector> myDetectorsById;
  private volatile MultiMap<FileType, Pair<ElementPattern<FileContent>, String>> myDetectorsMap;
  private volatile MultiMap<FileType, String> myDetectorsByFileType;
  private volatile FileType[] myAcceptedTypes;
  private volatile boolean myLoaded;

  private final Object myInitializationLock = new Object();

  public FrameworkDetectorRegistryImpl() {
    FrameworkDetector.EP_NAME.addChangeListener(() -> onDetectorsChanged(), this);
  }

  private synchronized void ensureDetectorsLoaded() {
    if (myLoaded) return;
    synchronized (myInitializationLock) {
      if (!myLoaded) {
        loadDetectors();
        myLoaded = true;
      }
    }
  }

  private void loadDetectors() {
    myDetectorsById = new HashMap<>();
    myDetectorsByFileType = new MultiMap<>();
    myDetectorsMap = new MultiMap<>();

    for (FrameworkDetector detector : FrameworkDetector.EP_NAME.getExtensionList()) {
      myDetectorsById.put(detector.getDetectorId(), detector);
      myDetectorsByFileType.putValue(detector.getFileType(), detector.getDetectorId());

      myDetectorsMap.putValue(detector.getFileType(), Pair.create(detector.createSuitableFilePattern(), detector.getDetectorId()));

      LOG.debug("'" + detector.getDetectorId() + "' framework detector registered");
    }

    myAcceptedTypes = myDetectorsByFileType.keySet().toArray(FileType.EMPTY_ARRAY);
  }

  @Override
  public @NotNull MultiMap<FileType, Pair<ElementPattern<FileContent>, String>> getDetectorsMap() {
    ensureDetectorsLoaded();
    return myDetectorsMap;
  }

  @Override
  public FileType @NotNull [] getAcceptedFileTypes() {
    ensureDetectorsLoaded();
    return myAcceptedTypes;
  }

  private void onDetectorsChanged() {
    synchronized (myInitializationLock) {
      myAcceptedTypes = null;
      myDetectorsMap = null;
      myDetectorsByFileType = null;
      myDetectorsById = null;
      myLoaded = false;
    }
  }

  @Override
  public FrameworkType findFrameworkType(@NotNull String typeId) {
    for (FrameworkType type : getFrameworkTypes()) {
      if (typeId.equals(type.getId())) {
        return type;
      }
    }
    return null;
  }

  @Override
  public @NotNull List<? extends FrameworkType> getFrameworkTypes() {
    List<FrameworkType> types = new ArrayList<>();
    for (FrameworkDetector detector : FrameworkDetector.EP_NAME.getExtensionList()) {
      types.add(detector.getFrameworkType());
    }
    return types;
  }

  @Override
  public FrameworkDetector getDetectorById(@NotNull String id) {
    ensureDetectorsLoaded();
    return myDetectorsById.get(id);
  }

  @Override
  public @NotNull Collection<String> getDetectorIds(@NotNull FileType fileType) {
    ensureDetectorsLoaded();
    return myDetectorsByFileType.get(fileType);
  }

  @Override
  public @NotNull Collection<String> getAllDetectorIds() {
    ensureDetectorsLoaded();
    return myDetectorsById.keySet();
  }

  @Override
  public void dispose() {
  }
}
