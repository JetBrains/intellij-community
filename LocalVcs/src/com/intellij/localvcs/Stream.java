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

  public Path readPath() throws IOException {
    return new Path(myIs);
  }

  public void writePath(Path p) throws IOException {
    p.write(myOs);
  }

  public Entry readEntry() throws IOException {
    return Entry.read(myIs);
  }

  public void writeEntry(Entry e) throws IOException {
    e.write(myOs);
  }

  // todo get rid of these two methods
  public Entry readRootEntry() throws IOException {
    return new RootEntry(myIs);
  }

  public void writeRootEntry(Entry e) throws IOException {
    e.write(myOs);
  }

  public Change readChange() throws IOException {
    return Change.read(myIs);
  }

  public void writeChange(Change c) throws IOException {
    c.write(myOs);
  }

  public String readString() throws IOException {
    return myIs.readUTF();
  }

  public void writeString(String s) throws IOException {
    myOs.writeUTF(s);
  }
}
