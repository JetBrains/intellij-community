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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class FileAttribute {
  private static final Set<String> ourRegisteredIds = new HashSet<String>();
  private final String myId;
  private final int myVersion;

  public FileAttribute(@NonNls final String id, int version) {
    myId = id;
    myVersion = version;
    boolean added = ourRegisteredIds.add(id);
    assert added : "Attribute id='" + id+ "' is not unique";
  }

  @Nullable
  public DataInputStream readAttribute(VirtualFile file) {
    final DataInputStream stream = ManagingFS.getInstance().readAttribute(file, this);
    if (stream != null) {
      try {
        int actualVersion = stream.readInt();
        if (actualVersion != myVersion) {
          stream.close();
          return null;
        }
      }
      catch (IOException e) {
        return null;
      }
    }
    return stream;
  }

  public DataOutputStream writeAttribute(VirtualFile file) {
    final DataOutputStream stream = ManagingFS.getInstance().writeAttribute(file, this);
    try {
      stream.writeInt(myVersion);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    return stream;
  }

  @Nullable
  public byte[] readAttributeBytes(VirtualFile file) throws IOException {
    final DataInputStream stream = readAttribute(file);
    if (stream == null) return null;

    try {
      int len = stream.readInt();
      return FileUtil.loadBytes(stream, len);
    }
    finally {
      stream.close();
    }
  }

  public void writeAttributeBytes(VirtualFile file, byte[] bytes) throws IOException {
    writeAttributeBytes(file, bytes, 0, bytes.length);
  }

  public void writeAttributeBytes(VirtualFile file, byte[] bytes, int offset, int len) throws IOException {
    final DataOutputStream stream = writeAttribute(file);
    try {
      stream.writeInt(len);
      stream.write(bytes, offset, len);
    }
    finally {
      stream.close();
    }
  }

  public String getId() {
    return myId;
  }
}