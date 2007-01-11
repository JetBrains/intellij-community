package com.intellij.localvcs;

import java.io.IOException;

public interface IContentStorage {
  void close() throws IOException;

  void save() throws IOException;

  int store(byte[] content) throws IOException;

  byte[] load(int id) throws IOException;

  void remove(int id) throws IOException;
}
