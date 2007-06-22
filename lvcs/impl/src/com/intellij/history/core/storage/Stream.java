package com.intellij.history.core.storage;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.changes.*;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;
import com.intellij.history.core.tree.RootEntry;

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
        return new StoredContent(this);
      case 1:
        return new UnavailableContent();
    }
    throw new IOException();
  }

  public void writeContent(Content content) throws IOException {
    int id = -1;

    Class c = content.getClass();
    if (c.equals(StoredContent.class)) id = 0;
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
        return new ChangeSet(this);
      case 1:
        return new CreateFileChange(this);
      case 2:
        return new CreateDirectoryChange(this);
      case 3:
        return new ChangeFileContentChange(this);
      case 4:
        return new RenameChange(this);
      case 5:
        return new MoveChange(this);
      case 6:
        return new DeleteChange(this);
      case 7:
        return new PutLabelChange(this);
      case 8:
        return new PutEntryLabelChange(this);
    }
    throw new IOException();
  }

  public void writeChange(Change change) throws IOException {
    int id = -1;

    Class c = change.getClass();
    if (c.equals(ChangeSet.class)) id = 0;
    if (c.equals(CreateFileChange.class)) id = 1;
    if (c.equals(CreateDirectoryChange.class)) id = 2;
    if (c.equals(ChangeFileContentChange.class)) id = 3;
    if (c.equals(RenameChange.class)) id = 4;
    if (c.equals(MoveChange.class)) id = 5;
    if (c.equals(DeleteChange.class)) id = 6;
    if (c.equals(PutLabelChange.class)) id = 7;
    if (c.equals(PutEntryLabelChange.class)) id = 8;

    myOs.writeInt(id);
    change.write(this);
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

  public boolean readBoolean() throws IOException {
    return myIs.readBoolean();
  }

  public void writeBoolean(boolean b) throws IOException {
    myOs.writeBoolean(b);
  }
}
