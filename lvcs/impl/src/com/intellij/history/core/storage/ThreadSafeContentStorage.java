package com.intellij.history.core.storage;

import java.io.IOException;

public class ThreadSafeContentStorage implements IContentStorage {
  private IContentStorage mySubject;

  public ThreadSafeContentStorage(IContentStorage s) {
    mySubject = s;
  }

  public synchronized void close() {
    mySubject.close();
  }

  public synchronized void save() {
    mySubject.save();
  }

  public synchronized int store(byte[] content) throws IOException {
    return mySubject.store(content);
  }

  public synchronized byte[] load(int id) throws IOException {
    return mySubject.load(id);
  }

  public synchronized void remove(int id) {
    mySubject.remove(id);
  }

  public synchronized boolean isRemoved(int id) {
    return mySubject.isRemoved(id);
  }
}
