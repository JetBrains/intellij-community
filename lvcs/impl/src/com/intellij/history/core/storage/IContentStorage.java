package com.intellij.history.core.storage;

import java.io.IOException;

public interface IContentStorage {
  void save();

  void close();

  int store(byte[] content) throws IOException;

  byte[] load(int id) throws IOException;

  void remove(int id);

  void setVersion(int version);

  int getVersion();
}
