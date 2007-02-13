package com.intellij.localvcs;

import java.io.IOException;

public class ThreadSafeContentStorage implements IContentStorage {
  private IContentStorage mySubject;

  public ThreadSafeContentStorage(IContentStorage s) {
    mySubject = s;
  }

  public synchronized void close() throws IOException {
    mySubject.close();
  }

  public synchronized void save() throws IOException {
    mySubject.save();
  }

  public synchronized int store(byte[] content) throws IOException {
    return mySubject.store(content);
  }

  public synchronized byte[] load(int id) throws IOException {
    return mySubject.load(id);
  }

  public synchronized void remove(int id) throws IOException {
    mySubject.remove(id);
  }

  public synchronized boolean has(int id) throws IOException {
    return mySubject.has(id);
  }
}
