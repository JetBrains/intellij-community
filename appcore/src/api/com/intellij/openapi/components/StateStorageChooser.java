package com.intellij.openapi.components;

public interface StateStorageChooser<T> {
  Storage[] selectStorages(Storage[] storages, T component, final StateStorageOperation operation);
}
