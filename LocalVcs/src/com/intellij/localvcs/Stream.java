package com.intellij.localvcs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Stream {
  private DataInputStream myIs;
  private DataOutputStream myOs;

  public Stream(InputStream is) {
    myIs = new DataInputStream(is);
  }

  public Stream(OutputStream os) {
    myOs = new DataOutputStream(os);
  }

  public void flush() throws IOException {
    myOs.flush();
  }

  public Path readPath() throws IOException {
    return new Path(this);
  }

  public void writePath(Path p) throws IOException {
    p.write(this);
  }

  public Entry readEntry() throws IOException {
    return Entry.read(this);
  }

  public void writeEntry(Entry e) throws IOException {
    e.write(this);
  }

  // todo get rid of these two methods
  public Entry readRootEntry() throws IOException {
    return new RootEntry(this);
  }

  public void writeRootEntry(Entry e) throws IOException {
    e.write(this);
  }

  public Change readChange() throws IOException {
    return Change.read(this);
  }

  public void writeChange(Change c) throws IOException {
    c.write(this);
  }

  public String readString() throws IOException {
    return myIs.readUTF();
  }

  public void writeString(String s) throws IOException {
    myOs.writeUTF(s);
  }

  public String readNullableString() throws IOException {
    if (!myIs.readBoolean()) return null;
    return myIs.readUTF();
  }

  public void writeNullableString(String s) throws IOException {
    myOs.writeBoolean(s != null);
    if (s != null) myOs.writeUTF(s);
  }

  public Boolean readBoolean() throws IOException {
    return myIs.readBoolean();
  }

  public void writeBoolean(Boolean b) throws IOException {
    myOs.writeBoolean(b);
  }

  public Integer readInteger() throws IOException {
    return myIs.readInt();
  }

  public void writeInteger(Integer i) throws IOException {
    myOs.writeInt(i);
  }
}
