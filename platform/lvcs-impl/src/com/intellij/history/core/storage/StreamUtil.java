/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.history.core.storage;

import com.intellij.history.core.changes.*;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class StreamUtil {
  public static Entry readEntry(DataInput in) throws IOException {
    int type = in.readInt();
    switch (type) {
      case 0:
        return new FileEntry(in, true);
      case 1:
        return new DirectoryEntry(in, true);
    }
    throw new IOException("unexpected entry type: " + type);
  }

  public static void writeEntry(DataOutput out, Entry e) throws IOException {
    int id = -1;

    Class c = e.getClass();
    if (c.equals(FileEntry.class)) id = 0;
    if (c.equals(DirectoryEntry.class)) id = 1;

    if (id == -1) throw new IOException("unexpected entry type: " + c);

    out.writeInt(id);
    e.write(out);
  }

  public static Change readChange(DataInput in) throws IOException {
    int type = in.readInt();
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

    out.writeInt(id);
    change.write(out);
  }

  public static String readString(DataInput in) throws IOException {
    return in.readUTF();
  }

  public static void writeString(DataOutput out, String s) throws IOException {
    out.writeUTF(s);
  }

  public static String readStringOrNull(DataInput in) throws IOException {
    if (!in.readBoolean()) return null;
    return readString(in);
  }

  public static void writeStringOrNull(DataOutput out, String s) throws IOException {
    out.writeBoolean(s != null);
    if (s != null) writeString(out, s);
  }
}
