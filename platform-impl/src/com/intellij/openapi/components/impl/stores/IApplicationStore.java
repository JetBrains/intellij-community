package com.intellij.openapi.components.impl.stores;

public interface IApplicationStore extends IComponentStore {
  void setOptionsPath(String path);

  StateStorageManager getStateStorageManager();

  void setConfigPath(final String configPath);
}
