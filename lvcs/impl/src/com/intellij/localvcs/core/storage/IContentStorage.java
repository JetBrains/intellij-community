package com.intellij.localvcs.core.storage;

import java.io.IOException;

// todo get rid of isRemoved()
public interface IContentStorage {
  int MAX_CONTENT_LENGTH = 1024 * 1024;

  void close();

  void save();

  int store(byte[] content) throws IOException;

  byte[] load(int id) throws IOException;

  void remove(int id);

  boolean isRemoved(int id);
}
