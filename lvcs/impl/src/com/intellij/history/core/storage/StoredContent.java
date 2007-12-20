package com.intellij.history.core.storage;

import java.io.IOException;

public class StoredContent extends Content {
  private Storage myStorage;
  private int myId;

  public StoredContent(Storage s, int id) {
    myStorage = s;
    myId = id;
  }

  public StoredContent(Stream s) throws IOException {
    myId = s.readInteger();
    myStorage = s.getStorage();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writeInteger(myId);
  }

  @Override
  public byte[] getBytes() {
    try {
      return getBytesUnsafe();
    }
    catch (BrokenStorageException e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] getBytesUnsafe() throws BrokenStorageException {
    return myStorage.loadContentData(myId);
  }

  @Override
  public boolean isAvailable() {
    try {
      getBytesUnsafe();
      return true;
    }
    catch (BrokenStorageException e) {
      return false;
    }
  }

  public int getId() {
    return myId;
  }

  @Override
  public void purge() {
    myStorage.purgeContent(this);
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o) && myId == ((StoredContent)o).myId;
  }

  @Override
  public int hashCode() {
    return myId;
  }
}