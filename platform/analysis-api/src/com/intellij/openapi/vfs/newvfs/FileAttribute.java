// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FileAttribute {
  private static final Set<String> ourRegisteredIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private static final int UNDEFINED_VERSION = -1;
  private final String myId;
  private final int myVersion;
  private final boolean myFixedSize;

  public FileAttribute(@NonNls @NotNull String id) {
    this(id, UNDEFINED_VERSION, false);
  }

  public FileAttribute(@NonNls @NotNull String id, int version, boolean fixedSize) {
    this(version, fixedSize, id);
    boolean added = ourRegisteredIds.add(id);
    assert added : "Attribute id='" + id+ "' is not unique";
  }

  private FileAttribute(int version, boolean fixedSize,@NotNull String id) {
    myId = id;
    myVersion = version;
    myFixedSize = fixedSize;
  }

  @Nullable
  public DataInputStream readAttribute(@NotNull VirtualFile file) {
    return ManagingFS.getInstance().readAttribute(file, this);
  }

  @NotNull
  public DataOutputStream writeAttribute(@NotNull VirtualFile file) {
    return ManagingFS.getInstance().writeAttribute(file, this);
  }

  public byte @Nullable [] readAttributeBytes(VirtualFile file) throws IOException {
    try (DataInputStream stream = readAttribute(file)) {
      if (stream == null) return null;
      int len = stream.readInt();
      return FileUtil.loadBytes(stream, len);
    }
  }

  public void writeAttributeBytes(VirtualFile file, byte @NotNull [] bytes) throws IOException {
    writeAttributeBytes(file, bytes, 0, bytes.length);
  }

  public void writeAttributeBytes(VirtualFile file, byte[] bytes, int offset, int len) throws IOException {
    try (DataOutputStream stream = writeAttribute(file)) {
      stream.writeInt(len);
      stream.write(bytes, offset, len);
    }
  }

  @NotNull
  public String getId() {
    return myId;
  }

  public boolean isFixedSize() {
    return myFixedSize;
  }

  @NotNull
  public FileAttribute newVersion(int newVersion) {
    return new FileAttribute(newVersion, myFixedSize, myId);
  }

  public int getVersion() {
    return myVersion;
  }

  public boolean isVersioned() {
    return myVersion != UNDEFINED_VERSION;
  }
}
