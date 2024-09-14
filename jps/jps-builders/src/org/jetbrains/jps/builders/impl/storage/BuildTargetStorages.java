// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.impl.storage;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.CompositeStorageOwner;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BuildTargetStorages extends CompositeStorageOwner {
  private static final Logger LOG = Logger.getInstance(BuildTargetStorages.class);
  private final BuildTarget<?> myTarget;
  private final BuildDataPaths myPaths;
  private final ConcurrentMap<StorageProvider<? extends StorageOwner>, StorageOwner> myStorages = new ConcurrentHashMap<>();

  public BuildTargetStorages(@NotNull BuildTarget<?> target, @NotNull BuildDataPaths paths) {
    myTarget = target;
    myPaths = paths;
  }

  public @NotNull <S extends StorageOwner> S getOrCreateStorage(@NotNull StorageProvider<S> provider, PathRelativizerService relativizer) throws IOException {
    try {
      return (S)myStorages.computeIfAbsent(provider, _provider -> {
        try {
          return _provider.createStorage(myPaths.getTargetDataRootDir(myTarget), relativizer);
        }
        catch (IOException e) {
          throw new BuildDataCorruptedException(e);
        }
      });
    }
    catch (BuildDataCorruptedException e) {
      LOG.info(e);
      throw e.getCause();
    }
  } 

  public void close(final @NotNull StorageProvider<? extends StorageOwner> provider) throws IOException {
    final StorageOwner storage = myStorages.remove(provider);
    if (storage != null) {
      storage.close();
    }
  }

  @Override
  protected Iterable<StorageOwner> getChildStorages() {
    return () -> myStorages.values().iterator();
  }
}
