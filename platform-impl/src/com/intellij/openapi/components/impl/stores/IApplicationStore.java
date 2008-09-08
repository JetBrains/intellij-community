package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.util.Pair;

import java.util.Set;
import java.io.IOException;

public interface IApplicationStore extends IComponentStore {
  void setOptionsPath(String path);

  StateStorageManager getStateStorageManager();

  void setConfigPath(final String configPath);

  boolean reload(final Set<Pair<VirtualFile,StateStorage>> changedFiles) throws StateStorage.StateStorageException, IOException;
}
