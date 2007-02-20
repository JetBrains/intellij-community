package com.intellij.openapi.components;

public interface StateStorageChooser<T> {
  Storage selectStorage(Storage[] storages, T component, final Operation operation);

  enum Operation {
    READ, WRITE
  }
}
