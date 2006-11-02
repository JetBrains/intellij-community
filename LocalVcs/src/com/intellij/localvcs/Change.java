package com.intellij.localvcs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class Change {
  public static Change read(DataInputStream s) throws IOException {
    String clazz = s.readUTF();

    if (clazz.equals(CreateFileChange.class.getSimpleName()))
      return new CreateFileChange(s);

    if (clazz.equals(CreateDirectoryChange.class.getSimpleName()))
      return new CreateDirectoryChange(s);

    if (clazz.equals(DeleteChange.class.getSimpleName()))
      return new DeleteChange(s);

    throw new RuntimeException();
  }

  public void write(DataOutputStream s) throws IOException {
    s.writeUTF(getClass().getSimpleName());
  }

  public abstract void applyTo(Snapshot snapshot);

  public abstract void revertOn(Snapshot snapshot);
}
