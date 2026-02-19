// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class InvalidVirtualFileAccessException extends RuntimeException {

  public InvalidVirtualFileAccessException(@NotNull VirtualFile file) {
    super(composeMessage(file));
    initCause(getInvalidationTrace(file));
  }

  public InvalidVirtualFileAccessException(@NotNull String message) {
    super(message);
  }

  private static @NonNls String composeMessage(@NotNull VirtualFile file) {
    String url = file.getUrl();
    @NonNls String message = "Accessing invalid virtual file: " + url;
    String reason = getInvalidationReason(file);
    if (reason != null) {
      message += "; reason: " + reason;
    }
    try {
      VirtualFile found = VirtualFileManager.getInstance().findFileByUrl(url);
      message += "; original:" + hashCode(file) + "; found:" + hashCode(found);
      if (file.getUrl().startsWith("file:")) {
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

  private static final Key<String> INVALIDATION_REASON = Key.create("INVALIDATION_REASON");
  private static final Key<Throwable> INVALIDATION_TRACE = Key.create("INVALIDATION_TRACE");

  @Internal
  public static void appendInvalidationReason(@NotNull VirtualFile file, @NotNull String reason) {
    String oldReason = getInvalidationReason(file);
    if (oldReason == null) {
      file.putUserData(INVALIDATION_TRACE, ThrowableInterner.intern(new Throwable()));
    }
    file.putUserData(INVALIDATION_REASON, oldReason == null ? reason : oldReason + "; " + reason);
  }

  private static @Nullable Throwable getInvalidationTrace(@NotNull VirtualFile file) {
    return file.getUserData(INVALIDATION_TRACE);
  }

  @Internal
  public static @Nullable String getInvalidationReason(@NotNull VirtualFile file) {
    return file.getUserData(INVALIDATION_REASON);
  }
}
