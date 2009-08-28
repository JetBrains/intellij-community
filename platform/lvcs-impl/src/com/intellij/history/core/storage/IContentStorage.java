package com.intellij.history.core.storage;

public interface IContentStorage {
  void save();

  void close();

  int store(byte[] content) throws BrokenStorageException;

  byte[] load(int id) throws BrokenStorageException;

  void remove(int id);

  void setVersion(int version);

  int getVersion();
}
