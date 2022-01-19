// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;

public class FileAttribute {
  private static final Set<String> ourRegisteredIds = ContainerUtil.newConcurrentSet();
  private static final int UNDEFINED_VERSION = -1;
  private final String myId;
  private final int myVersion;
  private final boolean myFixedSize;
  private final boolean myShouldEnumerate;

  public FileAttribute(@NonNls @NotNull String id) {
    this(id, UNDEFINED_VERSION, false, false);
  }

  public FileAttribute(@NonNls @NotNull String id, int version, boolean fixedSize) {
    this(id, version, fixedSize, false);
  }

  public FileAttribute(@NonNls @NotNull String id, int version, boolean fixedSize, boolean shouldEnumerate) {
    this(version, fixedSize, id, shouldEnumerate);
    boolean added = ourRegisteredIds.add(id);
    assert added : "Attribute id='" + id+ "' is not unique";
  }

  private FileAttribute(int version, boolean fixedSize,@NotNull String id, boolean shouldEnumerate) {
    myId = id;
    myVersion = version;
    myFixedSize = fixedSize;
    // TODO enumerate all binary data if asked
    myShouldEnumerate = shouldEnumerate;
  }

  /**
   * @deprecated use {@link FileAttribute#readFileAttribute(VirtualFile)}
   */
  @Deprecated
  @Nullable
  public DataInputStream readAttribute(@NotNull VirtualFile file) {
    return ManagingFS.getInstance().readAttribute(file, this);
  }

  /**
   * @deprecated use {@link FileAttribute#writeFileAttribute(VirtualFile)}
   */
  @Deprecated
  @NotNull
  public DataOutputStream writeAttribute(@NotNull VirtualFile file) {
    return ManagingFS.getInstance().writeAttribute(file, this);
  }

  @Nullable
  public AttributeInputStream readFileAttribute(@NotNull VirtualFile file) {
    return ManagingFS.getInstance().readAttribute(file, this);
  }

  @NotNull
  public AttributeOutputStream writeFileAttribute(@NotNull VirtualFile file) {
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
    return new FileAttribute(newVersion, myFixedSize, myId, myShouldEnumerate);
  }

  public int getVersion() {
    return myVersion;
  }

  public boolean isVersioned() {
    return myVersion != UNDEFINED_VERSION;
  }

  public static void resetRegisteredIds() {
    ourRegisteredIds.clear();
  }
}
