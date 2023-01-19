// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.psi.FilePropertyKey;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @deprecated use {@link FilePropertyPusherBase}
 */
@ApiStatus.Internal
@ApiStatus.Experimental
@Deprecated(forRemoval = true)
public interface FileIntPropertyPusher<T> extends FilePropertyPusher<T> {
  @NotNull
  FileAttribute getAttribute();

  int toInt(@NotNull T property) throws IOException;

  @NotNull
  T fromInt(int val) throws IOException;

  @Override
  @NotNull
  default FilePropertyKey<T> getFilePropertyKey() {
    return new IntFilePropertyKey<>(this);
  }

  @Override
  default void persistAttribute(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull T actualValue) throws IOException {
    if (!IntFilePropertyKey.persistValue(this, fileOrDir, actualValue)) return;

    propertyChanged(project, fileOrDir, actualValue);
  }

  void propertyChanged(@NotNull Project project,
                       @NotNull VirtualFile fileOrDir,
                       @NotNull T actualProperty);
}

/**
 * Don't use outside {@link FileIntPropertyPusher#getFilePropertyKey()}.
 * Should be deleted together with the {@link FileIntPropertyPusher#getFileDataKey()}
 * ({@link FileIntPropertyPusher#getFilePropertyKey()} default body should also be deleted together with the {@link FileIntPropertyPusher#getFileDataKey()})
 */
@ApiStatus.Internal
@Deprecated(forRemoval = true)
class IntFilePropertyKey<T> implements FilePropertyKey<T> {
  private final FileIntPropertyPusher<T> delegate;

  IntFilePropertyKey(FileIntPropertyPusher<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public T getPersistentValue(@Nullable VirtualFile virtualFile) {
    return delegate.getFileDataKey().get(virtualFile);
  }

  @Override
  public boolean setPersistentValue(@Nullable VirtualFile virtualFile, T value) {
    try {
      if (virtualFile == null) return false;
      return persistValue(delegate, virtualFile, value);
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to persist value: " + value + " to vFile: " + virtualFile, e);
    }
  }

  static <T> boolean persistValue(@NotNull FileIntPropertyPusher<T> delegate, @NotNull VirtualFile fileOrDir, @NotNull T actualValue)
    throws IOException {
    try (DataInputStream stream = delegate.getAttribute().readAttribute(fileOrDir)) {
      if (stream != null) {
        int storedIntValue = DataInputOutputUtil.readINT(stream);
        if (storedIntValue == delegate.toInt(actualValue)) return false;
      }
    }

    try (DataOutputStream stream = delegate.getAttribute().writeAttribute(fileOrDir)) {
      DataInputOutputUtil.writeINT(stream, delegate.toInt(actualValue));
    }
    return true;
  }
}