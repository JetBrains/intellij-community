// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.core;

import com.intellij.history.core.changes.*;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class StreamUtil {
  public static Entry readEntry(DataInput in) throws IOException {
    int type = DataInputOutputUtil.readINT(in);
    switch (type) {
      case 0:
        return new FileEntry(in, true);
      case 1:
        return new DirectoryEntry(in, true);
    }
    throw new IOException("unexpected entry type: " + type);
  }

  public static void writeEntry(@NotNull DataOutput out, Entry e) throws IOException {
    int id = -1;

    Class c = e.getClass();
    if (c.equals(FileEntry.class)) id = 0;
    if (c.equals(DirectoryEntry.class)) id = 1;

    if (id == -1) throw new IOException("unexpected entry type: " + c);

    DataInputOutputUtil.writeINT(out, id);
    e.write(out);
  }

  public static Change readChange(DataInput in) throws IOException {
    int type = DataInputOutputUtil.readINT(in);
    switch (type) {
      case 1:
        return new CreateFileChange(in);
      case 2:
        return new CreateDirectoryChange(in);
      case 3:
        return new ContentChange(in);
      case 4:
        return new RenameChange(in);
      case 5:
        return new ROStatusChange(in);
      case 6:
        return new MoveChange(in);
      case 7:
        return new DeleteChange(in);
      case 8:
        return new PutLabelChange(in);
      case 9:
        return new PutSystemLabelChange(in);
    }
    throw new IOException("unexpected change type: " + type);
  }

  public static void writeChange(DataOutput out, Change change) throws IOException {
    int id = -1;

    Class c = change.getClass();
    if (c.equals(CreateFileChange.class)) id = 1;
    if (c.equals(CreateDirectoryChange.class)) id = 2;
    if (c.equals(ContentChange.class)) id = 3;
    if (c.equals(RenameChange.class)) id = 4;
    if (c.equals(ROStatusChange.class)) id = 5;
    if (c.equals(MoveChange.class)) id = 6;
    if (c.equals(DeleteChange.class)) id = 7;
    if (c.equals(PutLabelChange.class)) id = 8;
    if (c.equals(PutSystemLabelChange.class)) id = 9;

    if (id == -1) throw new IOException("unexpected change type: " + c);

    DataInputOutputUtil.writeINT(out, id);
    change.write(out);
  }

  @NotNull
  public static String readString(DataInput in) throws IOException {
    return IOUtil.readUTF(in);
  }

  public static void writeString(DataOutput out, @NotNull String s) throws IOException {
    IOUtil.writeUTF(out, s);
  }

  @Nullable
  public static String readStringOrNull(DataInput in) throws IOException {
    if (!in.readBoolean()) return null;
    return readString(in);
  }

  public static void writeStringOrNull(DataOutput out, @Nullable String s) throws IOException {
    out.writeBoolean(s != null);
    if (s != null) writeString(out, s);
  }
}
