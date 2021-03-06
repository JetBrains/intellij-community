// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class InvalidVirtualFileAccessException extends RuntimeException {
  public InvalidVirtualFileAccessException(@NotNull VirtualFile file) {
    super(composeMessage(file));
  }

  public InvalidVirtualFileAccessException(@NotNull String message) {
    super(message);
  }

  private static @NonNls String composeMessage(@NotNull VirtualFile file) {
    String url = file.getUrl();
    @NonNls String message = "Accessing invalid virtual file: " + url;

    try {
      VirtualFile found = VirtualFileManager.getInstance().findFileByUrl(url);
      message += "; original:" + hashCode(file) + "; found:" + hashCode(found);
      if (file.isInLocalFileSystem()) {
        boolean physicalExists = new File(file.getPath()).exists();
        message += "; File.exists()=" + physicalExists;
      }
      else {
        message += "; file system=" + file.getFileSystem();
      }
    }
    catch (Throwable t) {
      message += "; lookup failed: " + t.getMessage();
    }

    return message;
  }

  private static String hashCode(Object o) {
    return o == null ? "-" : String.valueOf(o.hashCode());
  }
}