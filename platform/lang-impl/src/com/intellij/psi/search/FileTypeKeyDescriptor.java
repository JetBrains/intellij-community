// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexEx;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.SubstitutedFileType;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;

@ApiStatus.Internal
abstract public class FileTypeKeyDescriptor implements KeyDescriptor<FileType> {
  protected abstract @NotNull FileTypeNameEnumerator getEnumerator();

  @Override
  public int getHashCode(FileType value) {
    return value.getName().hashCode();
  }

  @Override
  public boolean isEqual(FileType val1, FileType val2) {
    if (val1 instanceof SubstitutedFileType) val1 = ((SubstitutedFileType)val1).getFileType();
    if (val2 instanceof SubstitutedFileType) val2 = ((SubstitutedFileType)val2).getFileType();
    if (val1 instanceof OutDatedFileType || val2 instanceof OutDatedFileType) {
      return Objects.equals(val1.getName(), val2.getName());
    }
    return Comparing.equal(val1, val2);
  }

  @Override
  public void save(@NotNull DataOutput out, FileType value) throws IOException {
    DataInputOutputUtil.writeINT(out, getFileTypeId(value.getName()));
  }

  @Override
  public FileType read(@NotNull DataInput in) throws IOException {
    String read = getFileTypeName(DataInputOutputUtil.readINT(in));
    if (read == null) {
      return UnknownFileType.INSTANCE;
    }
    FileType fileType = FileTypeRegistry.getInstance().findFileTypeByName(read);
    return fileType == null ? new OutDatedFileType(read) : fileType;
  }

  int getFileTypeId(@NotNull String fileTypeName) throws IOException {
    return getEnumerator().getFileTypeId(fileTypeName);
  }

  @Nullable String getFileTypeName(int fileTypeId) throws IOException {
    return getEnumerator().getFileTypeName(fileTypeId);
  }

  private static final class OutDatedFileType implements FileType {
    private final @NotNull String myName;

    private OutDatedFileType(@NotNull String name) {myName = name;}

    @Override
    public @NotNull String getName() {
      return myName;
    }

    @Override
    public @NotNull String getDescription() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull String getDefaultExtension() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Icon getIcon() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBinary() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReadOnly() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
      throw new UnsupportedOperationException();
    }
  }
}
