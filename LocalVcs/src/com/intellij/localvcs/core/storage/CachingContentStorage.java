package com.intellij.localvcs.core.storage;

import com.intellij.util.containers.IntObjectCache;

import java.io.IOException;

public class CachingContentStorage implements IContentStorage {
  private IContentStorage mySubject;
  private IntObjectCache<byte[]> myCache;

  public CachingContentStorage(IContentStorage s) {
    mySubject = s;
    myCache = new IntObjectCache<byte[]>(200);
  }

  public void close() {
    mySubject.close();
  }

  public void save() {
    mySubject.save();
  }

  public int store(byte[] content) throws IOException {
    int id = mySubject.store(content);
    myCache.cacheObject(id, content);
    return id;
  }

  public byte[] load(int id) throws IOException {
    byte[] result = findInCache(id);
    if (result != null) return result;

    result = mySubject.load(id);
    myCache.cacheObject(id, result);
    return result;
  }

  private byte[] findInCache(int id) {
    return myCache.tryKey(id);
  }

  public void remove(int id) {
    mySubject.remove(id);
    myCache.remove(id);
  }

  public boolean isRemoved(int id) {
    return mySubject.isRemoved(id);
  }
}
