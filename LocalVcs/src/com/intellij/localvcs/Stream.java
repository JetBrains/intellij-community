package com.intellij.localvcs;

import java.io.*;

// todo remove all null-saves and replace with wrapper for tests!!!!
public class Stream {
  private DataInputStream myIs;
  private DataOutputStream myOs;
  private LocalVcsStorage myStorage;

  public Stream(InputStream is, LocalVcsStorage s) {
    myStorage = s;
    myIs = new DataInputStream(is);
  }

  public Stream(OutputStream os) {
    myOs = new DataOutputStream(os);
  }

  public LocalVcsStorage getStorage() {
    return myStorage;
  }

  public IdPath readIdPath() throws IOException {
    return new IdPath(this);
  }

  public void writeIdPath(IdPath p) throws IOException {
    p.write(this);
  }

  public Content readContent() throws IOException {
    if (!myIs.readBoolean()) return null;
    switch (myIs.readInt()) {
      case 0:
        return new Content(this);
      case 1:
        return new UnavailableContent(this);
    }
    return null;
  }

  public void writeContent(Content content) throws IOException {
    myOs.writeBoolean(content != null);
    if (content == null) return;

    Integer id = null;

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
    return null;
  }

  public void writeEntry(Entry e) throws IOException {
    Integer id = null;

    Class c = e.getClass();
    if (c.equals(FileEntry.class)) id = 0;
    if (c.equals(DirectoryEntry.class)) id = 1;
    if (c.equals(RootEntry.class)) id = 2;

    myOs.writeInt(id);
    e.write(this);
  }

  public Change readChange() throws IOException {
    // todo use map and reflection
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
    return null;
  }

  public void writeChange(Change change) throws IOException {
    Integer id = null;

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
    // todo remove null-saving after refactoring RootEntry 
    if (!myIs.readBoolean()) return null;
    return myIs.readUTF();
  }

  public void writeString(String s) throws IOException {
    // todo make it not-nullable
    // todo writeUTF is very time consuming
    myOs.writeBoolean(s != null);
    if (s != null) myOs.writeUTF(s);
  }

  public Integer readInteger() throws IOException {
    if (!myIs.readBoolean()) return null;
    return myIs.readInt();
  }

  public void writeInteger(Integer i) throws IOException {
    myOs.writeBoolean(i != null);
    if (i != null) myOs.writeInt(i);
  }

  public Long readLong() throws IOException {
    if (!myIs.readBoolean()) return null;
    return myIs.readLong();
  }

  public void writeLong(Long l) throws IOException {
    myOs.writeBoolean(l != null);
    if (l != null) myOs.writeLong(l);
  }
}
