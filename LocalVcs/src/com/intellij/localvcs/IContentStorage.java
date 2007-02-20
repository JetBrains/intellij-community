package com.intellij.localvcs;

import java.io.IOException;

// todo get rid of has()

// todo get rid of exceptions
public interface IContentStorage {
  void close();

  void save();

  int store(byte[] content) throws IOException;

  byte[] load(int id) throws IOException;

  void remove(int id);

  boolean isRemoved(int id);
}
