package com.intellij.history.core.storage;

public class ThreadSafeContentStorage implements IContentStorage {
  private IContentStorage mySubject;

  public ThreadSafeContentStorage(IContentStorage s) {
    mySubject = s;
  }

  public synchronized void save() {
    mySubject.save();
  }

  public synchronized void close() {
    mySubject.close();
  }

  public synchronized int store(byte[] content) throws BrokenStorageException {
    return mySubject.store(content);
  }

  public synchronized byte[] load(int id) throws BrokenStorageException {
    return mySubject.load(id);
  }

  public synchronized void remove(int id) {
    mySubject.remove(id);
  }

  public synchronized void setVersion(final int version) {
    mySubject.setVersion(version);
  }

  public synchronized int getVersion() {
    return mySubject.getVersion();
  }
}
