// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Identifies a file attribute in {@link ManagingFS}. Conceptually, every file in {@link ManagingFS} has a set of attributes,
 * identified by {@link FileAttribute}.
 * Attribute has a name and a version.
 * Version is used to identify changes in attribute content's binary format: if attribute's version is different from the
 * stored one, stored attribute content is treated as non-existent.
 * <br/>
 * This class ctor prohibit creation of >1 instances with the same id.
 * In general, objects of this class should be created as static final constants -- i.e. it should be limited number of
 * instances, and no chance of occasionally creating duplicates.
 *
 * @see ManagingFS#readAttribute(VirtualFile, FileAttribute)
 * @see ManagingFS#writeAttribute(VirtualFile, FileAttribute)
 */
public class FileAttribute {
  private static final int UNDEFINED_VERSION = -1;

  private static final Set<String> registeredAttributeIds = ConcurrentHashMap.newKeySet();

  private final String id;
  private final int version;

  /**
   * Indicates that attribute content ({@link #writeAttributeBytes(VirtualFile, byte[])}) are of fixed size.
   * This serves as a hint for storage allocation: for fixed-size attributes space could be allocated
   * without reserve for future extension.
   */
  private final boolean fixedSize;

  public FileAttribute(@NonNls @NotNull String id) {
    this(id, UNDEFINED_VERSION, false, false);
  }

  public FileAttribute(@NonNls @NotNull String id, int version, boolean fixedSize) {
    this(id, version, fixedSize, false);
  }

  /** @deprecated use {@link FileAttribute#FileAttribute(String, int, boolean)} -- shouldEnumerate is ignored (was never implemented) */
  @Deprecated
  public FileAttribute(@NonNls @NotNull String id,
                       int version,
                       boolean fixedSize,
                       @SuppressWarnings("unused") boolean shouldEnumerate) {
    this(version, fixedSize, id);
    boolean added = registeredAttributeIds.add(id);
    assert added : "Attribute id='" + id + "' is not unique";
  }

  private FileAttribute(int version, boolean fixedSize, @NotNull String id) {
    this.id = id;
    this.version = version;
    this.fixedSize = fixedSize;
  }

  /**
   * @deprecated use {@link FileAttribute#readFileAttribute(VirtualFile)}
   */
  @Deprecated
  public @Nullable DataInputStream readAttribute(@NotNull VirtualFile file) {
    return ManagingFS.getInstance().readAttribute(file, this);
  }

  /**
   * @deprecated use {@link FileAttribute#writeFileAttribute(VirtualFile)}
   */
  @Deprecated
  public @NotNull DataOutputStream writeAttribute(@NotNull VirtualFile file) {
    return ManagingFS.getInstance().writeAttribute(file, this);
  }

  public @Nullable AttributeInputStream readFileAttribute(@NotNull VirtualFile file) {
    return ManagingFS.getInstance().readAttribute(file, this);
  }

  public @NotNull AttributeOutputStream writeFileAttribute(@NotNull VirtualFile file) {
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

  public @NotNull String getId() {
    return id;
  }

  public boolean isFixedSize() {
    return fixedSize;
  }

  public @NotNull FileAttribute newVersion(int newVersion) {
    return new FileAttribute(newVersion, fixedSize, id);
  }

  public int getVersion() {
    return version;
  }

  public boolean isVersioned() {
    return version != UNDEFINED_VERSION;
  }

  public static void resetRegisteredIds() {
    registeredAttributeIds.clear();
  }

  @Override
  public String toString() {
    return "FileAttribute[" + id + "]" +
           "{version: " + version +
           ", fixedSize: " + fixedSize +
           '}';
  }
}
