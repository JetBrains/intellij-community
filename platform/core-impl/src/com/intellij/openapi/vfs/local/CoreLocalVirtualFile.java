// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;

public class CoreLocalVirtualFile extends VirtualFile {
  private final CoreLocalFileSystem myFileSystem;
  private final Path myFile;
  private BasicFileAttributes myAttributes;
  private VirtualFile[] myChildren;

  public CoreLocalVirtualFile(@NotNull CoreLocalFileSystem fileSystem, @NotNull File ioFile) {
    this(fileSystem, ioFile.toPath());
  }

  public CoreLocalVirtualFile(@NotNull CoreLocalFileSystem fileSystem, @NotNull File ioFile, boolean isDirectory) {
    this(fileSystem, ioFile.toPath(), isDirectory);
  }

  public CoreLocalVirtualFile(@NotNull CoreLocalFileSystem fileSystem, @NotNull Path file) {
    myFileSystem = fileSystem;
    myFile = file;
  }

  public CoreLocalVirtualFile(@NotNull CoreLocalFileSystem fileSystem, @NotNull Path file, boolean isDirectory) {
    myFileSystem = fileSystem;
    myFile = file;
    myAttributes = isDirectory ? new IncompleteDirectoryAttributes() : null;
  }

  public CoreLocalVirtualFile(@NotNull CoreLocalFileSystem fileSystem, @NotNull Path file, @NotNull BasicFileAttributes attributes) {
    myFileSystem = fileSystem;
    myFile = file;
    myAttributes = attributes;
  }

  protected @NotNull Path getFile() {
    return myFile;
  }

  @Override
  public @NotNull VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  @Override
  public @NotNull String getName() {
    return NioFiles.getFileName(myFile);
  }

  @Override
  public @NotNull String getPath() {
    return FileUtil.toSystemIndependentName(myFile.toString());
  }

  @Override
  public boolean isWritable() {
    return false; // Core VFS isn't writable.
  }

  @Override
  public boolean isDirectory() {
    BasicFileAttributes attrs = getAttributes(false);
    return attrs != null && attrs.isDirectory();
  }

  @Override
  public boolean is(@NotNull VFileProperty property) {
    BasicFileAttributes attrs = getAttributes(true);
    if (property == VFileProperty.HIDDEN) {
      return attrs instanceof DosFileAttributes && ((DosFileAttributes)attrs).isHidden() ||
             NioFiles.getFileName(myFile).startsWith(".");
    }
    if (property == VFileProperty.SYMLINK) return attrs != null && attrs.isSymbolicLink();
    if (property == VFileProperty.SPECIAL) return attrs != null && attrs.isOther();
    return super.is(property);
  }

  @Override
  public long getTimeStamp() {
    BasicFileAttributes attrs = getAttributes(true);
    return attrs != null ? attrs.lastModifiedTime().toMillis() : -1;
  }

  @Override
  public long getLength() {
    BasicFileAttributes attrs = getAttributes(false);
    return attrs != null ? attrs.size() : -1;
  }

  protected @Nullable BasicFileAttributes getAttributes(boolean full) {
    if (myAttributes == null || full && myAttributes instanceof IncompleteDirectoryAttributes) {
      try {
        myAttributes = Files.readAttributes(myFile, BasicFileAttributes.class);
      }
      catch (IOException ignored) { }
    }
    return myAttributes;
  }

  @Override
  public boolean isValid() {
    return true; // Core VFS cannot change, doesn't refresh so once found, any file is writable
  }

  @Override
  public VirtualFile getParent() {
    Path parentFile = myFile.getParent();
    return parentFile != null ? new CoreLocalVirtualFile(myFileSystem, parentFile, new IncompleteDirectoryAttributes()) : null;
  }

  @Override
  public VirtualFile[] getChildren() {
    if (myChildren == null) {
      List<Path> files = NioFiles.list(myFile);
      if (files.isEmpty()) {
        myChildren = EMPTY_ARRAY;
      }
      else {
        VirtualFile[] result = new VirtualFile[files.size()];
        for (int i = 0; i < files.size(); i++) result[i] = new CoreLocalVirtualFile(myFileSystem, files.get(i));
        myChildren = result;
      }
    }
    return myChildren;
  }

  @Override
  public @NotNull OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    return Files.readAllBytes(myFile);
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) { }

  @Override
  public @NotNull InputStream getInputStream() throws IOException {
    return VfsUtilCore.inputStreamSkippingBOM(new BufferedInputStream(Files.newInputStream(myFile)), this);
  }

  @Override
  public long getModificationStamp() {
    return 0;
  }

  @Override
  public boolean isInLocalFileSystem() {
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CoreLocalVirtualFile that = (CoreLocalVirtualFile)o;

    return myFile.equals(that.myFile);
  }

  @Override
  public int hashCode() {
    return myFile.hashCode();
  }

  private static final class IncompleteDirectoryAttributes implements BasicFileAttributes {
    @Override
    public FileTime lastModifiedTime() {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileTime lastAccessTime() {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileTime creationTime() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isRegularFile() {
      return false;
    }

    @Override
    public boolean isDirectory() {
      return true;
    }

    @Override
    public boolean isSymbolicLink() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOther() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long size() {
      return 0;
    }

    @Override
    public Object fileKey() {
      throw new UnsupportedOperationException();
    }
  }
}
