package com.intellij.history.core.storage;

import com.intellij.util.io.PagedMemoryMappedFile;
import com.intellij.util.io.RandomAccessPagedDataInput;
import com.intellij.util.io.RecordDataOutput;

import java.io.File;
import java.io.IOException;

public class ContentStorage implements IContentStorage {
  private PagedMemoryMappedFile myStore;

  public ContentStorage(File f) throws IOException {
    myStore = new PagedMemoryMappedFile(f);
  }

  public static IContentStorage createContentStorage(File f) throws IOException {
    IContentStorage s = new ContentStorage(f);
    s = new CachingContentStorage(s);
    s = new CompressingContentStorage(s);
    s = new ThreadSafeContentStorage(s);
    return s;
  }

  public void close() {
    myStore.dispose();
  }

  public void save() {
    myStore.immediateForce();
  }

  public int store(byte[] content) throws IOException {
    try {
      RecordDataOutput r = myStore.createRecord();
      r.writeInt(content.length);
      r.write(content);
      r.close();
      return r.getRecordId();
    }
    catch (Exception e) {
      // todo AFTER JDK 1.6 throw new IOException(e);
      throw new IOException(e.getMessage());
    }
  }

  public byte[] load(int id) throws IOException {
    try {
      RandomAccessPagedDataInput r = myStore.getReader(id);
      byte[] buffer = new byte[r.readInt()];
      r.readFully(buffer);
      r.close();
      return buffer;
    }
    catch (Exception e) {
      // todo AFTER JDK 1.6 throw new IOException(e);
      throw new IOException(e.getMessage());
    }
  }

  public void remove(int id) {
    myStore.delete(id);
  }

  public boolean isRemoved(int id) {
    return myStore.isPageFree(id);
  }
}