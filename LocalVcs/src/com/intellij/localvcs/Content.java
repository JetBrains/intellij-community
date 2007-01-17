package com.intellij.localvcs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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
    return myStorage.loadContent(myId);
  }

  public int getLength() {
    // todo make it faster!!!
    return getBytes().length;
  }

  public InputStream getInputStream() {
    // todo make it faster!!!
    return new ByteArrayInputStream(getBytes());
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
