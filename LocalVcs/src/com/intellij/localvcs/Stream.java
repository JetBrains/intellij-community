package com.intellij.localvcs;

import java.io.*;

public class Stream {
  private DataInputStream myIs;
  private DataOutputStream myOs;
  private Storage myStorage;

  public Stream(InputStream is, Storage s) {
    myStorage = s;
    myIs = new DataInputStream(is);
  }

  public Stream(OutputStream os) {
    myOs = new DataOutputStream(os);
  }

  public Storage getStorage() {
    return myStorage;
  }

  public IdPath readIdPath() throws IOException {
    return new IdPath(this);
  }

  public void writeIdPath(IdPath p) throws IOException {
    p.write(this);
  }

  public Content readContent() throws IOException {
    switch (myIs.readInt()) {
      case 0:
        return new Content(this);
      case 1:
        return new UnavailableContent(this);
    }
    throw new IOException();
  }

  public void writeContent(Content content) throws IOException {
    int id = -1;

    Class c = content.getClass();
    if (c.equals(Content.class)) id = 0;
    if (c.equals(UnavailableContent.class)) id = 1;

    myOs.writeInt(id);
    content.write(this);
  }

  public Entry readEntry() throws IOException {
    switch (myIs.readInt()) {
      case 0:
        return new FileEntry(this);
      case 1:
        return new DirectoryEntry(this);
      case 2:
        return new RootEntry(this);
    }
    throw new IOException();
  }

  public void writeEntry(Entry e) throws IOException {
    int id = -1;

    Class c = e.getClass();
    if (c.equals(FileEntry.class)) id = 0;
    if (c.equals(DirectoryEntry.class)) id = 1;
    if (c.equals(RootEntry.class)) id = 2;

    myOs.writeInt(id);
    e.write(this);
  }

  public Change readChange() throws IOException {
    switch (myIs.readInt()) {
      case 0:
        return new CreateFileChange(this);
      case 1:
        return new CreateDirectoryChange(this);
      case 2:
        return new ChangeFileContentChange(this);
      case 3:
        return new RenameChange(this);
      case 4:
        return new MoveChange(this);
      case 5:
        return new DeleteChange(this);
    }
    throw new IOException();
  }

  public void writeChange(Change change) throws IOException {
    int id = -1;

    Class c = change.getClass();
    if (c.equals(CreateFileChange.class)) id = 0;
    if (c.equals(CreateDirectoryChange.class)) id = 1;
    if (c.equals(ChangeFileContentChange.class)) id = 2;
    if (c.equals(RenameChange.class)) id = 3;
    if (c.equals(MoveChange.class)) id = 4;
    if (c.equals(DeleteChange.class)) id = 5;

    myOs.writeInt(id);
    change.write(this);
  }

  public ChangeSet readChangeSet() throws IOException {
    return new ChangeSet(this);
  }

  public void writeChangeSet(ChangeSet c) throws IOException {
    c.write(this);
  }

  public ChangeList readChangeList() throws IOException {
    return new ChangeList(this);
  }

  public void writeChangeList(ChangeList c) throws IOException {
    c.write(this);
  }

  public String readString() throws IOException {
    return myIs.readUTF();
  }

  public void writeString(String s) throws IOException {
    // todo writeUTF is very time consuming
    myOs.writeUTF(s);
  }

  public String readStringOrNull() throws IOException {
    if (!myIs.readBoolean()) return null;
    return readString();
  }

  public void writeStringOrNull(String s) throws IOException {
    myOs.writeBoolean(s != null);
    if (s != null) writeString(s);
  }

  public int readInteger() throws IOException {
    return myIs.readInt();
  }

  public void writeInteger(int i) throws IOException {
    myOs.writeInt(i);
  }

  public long readLong() throws IOException {
    return myIs.readLong();
  }

  public void writeLong(long l) throws IOException {
    myOs.writeLong(l);
  }
}
