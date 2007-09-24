package com.intellij.history.core.storage;

import com.intellij.util.containers.IntObjectCache;

import java.io.IOException;

public class CachingContentStorage implements IContentStorage {
  public static final int MAX_CACHED_CONTENT_LENGTH = 100 * 1024;

  private IContentStorage mySubject;
  private IntObjectCache<byte[]> myCache = new IntObjectCache<byte[]>(50);

  public CachingContentStorage(IContentStorage s) {
    mySubject = s;
  }

  public void save() {
    mySubject.save();
  }

  public void close() {
    mySubject.close();
  }

  public int store(byte[] content) throws IOException {
    int id = mySubject.store(content);
    cacheContent(content, id);
    return id;
  }

  public byte[] load(int id) throws IOException {
    byte[] result = findInCache(id);
    if (result != null) return result;

    result = mySubject.load(id);
    cacheContent(result, id);
    return result;
  }

  private byte[] findInCache(int id) {
    return myCache.tryKey(id);
  }

  public void remove(int id) {
    mySubject.remove(id);
    myCache.remove(id);
  }

  public void setVersion(int version) {
    mySubject.setVersion(version);
  }

  public int getVersion() {
    return mySubject.getVersion();
  }

  private void cacheContent(byte[] content, int id) {
    if (content.length > MAX_CACHED_CONTENT_LENGTH) return;
    myCache.cacheObject(id, content);
  }
}
