// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@ApiStatus.Internal
@ApiStatus.Experimental
public interface FileIntPropertyPusher<T> extends FilePropertyPusher<T> {
  @NotNull
  FileAttribute getAttribute();

  int toInt(@NotNull T property) throws IOException;

  @NotNull
  T fromInt(int val) throws IOException;

  @Override
  default void persistAttribute(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull T actualValue) throws IOException {
    try (DataInputStream stream = getAttribute().readAttribute(fileOrDir)) {
      if (stream != null) {
        int storedIntValue = DataInputOutputUtil.readINT(stream);
        if (storedIntValue == toInt(actualValue)) return;
      }
    }

    try (DataOutputStream stream = getAttribute().writeAttribute(fileOrDir)) {
      DataInputOutputUtil.writeINT(stream, toInt(actualValue));
    }

    propertyChanged(project, fileOrDir, actualValue);
  }

  void propertyChanged(@NotNull Project project,
                       @NotNull VirtualFile fileOrDir,
                       @NotNull T actualProperty);
}