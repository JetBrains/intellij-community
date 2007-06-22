package com.intellij.history.core.storage;

import java.io.IOException;

// todo get rid of isRemoved()
public interface IContentStorage {
  void close();

  void save();

  int store(byte[] content) throws IOException;

  byte[] load(int id) throws IOException;

  void remove(int id);

  boolean isRemoved(int id);
}
