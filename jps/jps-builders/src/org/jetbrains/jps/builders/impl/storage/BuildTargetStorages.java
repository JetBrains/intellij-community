package org.jetbrains.jps.builders.impl.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.incremental.storage.CompositeStorageOwner;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class BuildTargetStorages extends CompositeStorageOwner {
  private final BuildTarget<?> myTarget;
  private final BuildDataPaths myPaths;
  private Map<StorageProvider<?>, StorageOwner> myStorages = new HashMap<StorageProvider<?>, StorageOwner>();

  public BuildTargetStorages(BuildTarget<?> target, BuildDataPaths paths) {
    myTarget = target;
    myPaths = paths;
  }

  @NotNull 
  public <S extends StorageOwner> S getOrCreateStorage(@NotNull StorageProvider<S> provider) throws IOException {
    //noinspection unchecked
    S storage = (S)myStorages.get(provider);
    if (storage == null) {
      storage = provider.createStorage(myPaths.getTargetDataRoot(myTarget));
      myStorages.put(provider, storage);
    }
    return (S)storage;
  } 
  
  @Override
  protected Iterable<? extends StorageOwner> getChildStorages() {
    return myStorages.values();
  }
}
