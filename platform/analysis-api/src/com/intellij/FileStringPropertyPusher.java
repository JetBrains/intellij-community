// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

@ApiStatus.Internal
@ApiStatus.Experimental
public interface FileStringPropertyPusher<T> extends FilePropertyPusher<T> {
  @NotNull
  FileAttribute getAttribute();

  String asString(@NotNull T property) throws IOException;

  @NotNull
  T fromString(String val) throws IOException;

  @Override
  default void persistAttribute(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull T actualValue) throws IOException {
    try (AttributeInputStream stream = getAttribute().readFileAttribute(fileOrDir)) {
      if (stream != null) {
        String storedValue = stream.readEnumeratedString();
        if (Objects.equals(storedValue, asString(actualValue))) return;
      }
    }

    try (AttributeOutputStream stream = getAttribute().writeFileAttribute(fileOrDir)) {
      stream.writeEnumeratedString(asString(actualValue));
    }

    propertyChanged(project, fileOrDir, actualValue);
  }

  void propertyChanged(@NotNull Project project,
                       @NotNull VirtualFile fileOrDir,
                       @NotNull T actualProperty);
}
