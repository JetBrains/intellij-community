// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.util.text.StringUtil;
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
    String actualStr = StringUtil.nullize(asString(actualValue));
    try (AttributeInputStream stream = getAttribute().readFileAttribute(fileOrDir)) {
      String storedValue = stream != null && stream.available() > 0 ? stream.readEnumeratedString() : null;
      /* actually it's always backed by baos */
      if (Objects.equals(storedValue, actualStr)) return;
    }

    try (AttributeOutputStream stream = getAttribute().writeFileAttribute(fileOrDir)) {
      if (actualStr != null) {
        stream.writeEnumeratedString(actualStr);
      }
    }

    propertyChanged(project, fileOrDir, actualValue);
  }

  void propertyChanged(@NotNull Project project,
                       @NotNull VirtualFile fileOrDir,
                       @NotNull T actualProperty);
}
