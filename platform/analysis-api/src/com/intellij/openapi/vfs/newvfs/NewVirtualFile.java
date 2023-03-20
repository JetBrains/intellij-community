// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;

public abstract class NewVirtualFile extends VirtualFile implements VirtualFileWithId {

  @Override
  public boolean isValid() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return exists();
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    throw new IOException("Cannot get content of " + this);
  }

  @Override
  public abstract @NotNull NewVirtualFileSystem getFileSystem();

  @Override
  public abstract NewVirtualFile getParent();

  @Override
  public abstract @Nullable NewVirtualFile getCanonicalFile();

  @Override
  public abstract @Nullable NewVirtualFile findChild(@NotNull @NonNls String name);

  public abstract @Nullable NewVirtualFile refreshAndFindChild(@NotNull String name);

  public abstract @Nullable NewVirtualFile findChildIfCached(@NotNull String name);


  public abstract void setTimeStamp(long time) throws IOException;

  @Override
  public abstract @NotNull CharSequence getNameSequence();

  @Override
  public abstract int getId();

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    RefreshQueue.getInstance().refresh(asynchronous, recursive, postRunnable, this);
  }

  @Override
  public abstract void setWritable(boolean writable) throws IOException;

  public abstract void markDirty();

  public abstract void markDirtyRecursively();

  public abstract boolean isDirty();

  @ApiStatus.Experimental
  public abstract boolean isOffline();

  @ApiStatus.Experimental
  public abstract void setOffline(boolean offline);

  public abstract void markClean();

  @Override
  public void move(Object requestor, @NotNull VirtualFile newParent) throws IOException {
    if (!exists()) {
      throw new IOException("File to move does not exist: " + getPath());
    }

    if (!newParent.exists()) {
      throw new IOException("Destination folder does not exist: " + newParent.getPath());
    }

    if (!newParent.isDirectory()) {
      throw new IOException("Destination is not a folder: " + newParent.getPath());
    }

    VirtualFile child = newParent.findChild(getName());
    if (child != null) {
      throw new IOException("Destination already exists: " + newParent.getPath() + "/" + getName());
    }

    EncodingRegistry.doActionAndRestoreEncoding(this, () -> {
      getFileSystem().moveFile(requestor, this, newParent);
      return this;
    });
  }

  public abstract @NotNull Collection<VirtualFile> getCachedChildren();

  @SuppressWarnings("SpellCheckingInspection")
  public abstract @NotNull Iterable<VirtualFile> iterInDbChildren();

  @ApiStatus.Internal
  @SuppressWarnings("SpellCheckingInspection")
  public @NotNull Iterable<VirtualFile> iterInDbChildrenWithoutLoadingVfsFromOtherProjects() {
    return iterInDbChildren();
  }
}
