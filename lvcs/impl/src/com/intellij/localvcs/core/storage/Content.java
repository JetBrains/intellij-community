package com.intellij.localvcs.core.storage;

import java.io.IOException;

public class Content {
  private Storage myStorage;
  private int myId;

  public Content(Storage s, int id) {
    myStorage = s;
    myId = id;
  }

  public Content(Stream s) throws IOException {
    myId = s.readInteger();
    myStorage = s.getStorage();
  }

  public void write(Stream s) throws IOException {
    s.writeInteger(myId);
  }

  public byte[] getBytes() {
    try {
      return getBytesUnsafe();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] getBytesUnsafe() throws IOException {
    return myStorage.loadContentData(myId);
  }

  public boolean isAvailable() {
    try {
      getBytesUnsafe();
      return true;
    }
    catch (IOException e) {
      return false;
    }
  }

  public int getId() {
    return myId;
  }

  public void purge() {
    myStorage.purgeContent(this);
  }

  @Override
  public String toString() {
    return new String(getBytes());
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !getClass().equals(o.getClass())) return false;
    return myId == ((Content)o).myId;
  }

  public int hashCode() {
    return myId;
  }
}
