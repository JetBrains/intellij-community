// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;

import java.io.IOError;
import java.io.UncheckedIOException;
import java.nio.file.*;

public final class IoErrorText {
  /**
   * In general, NIO2 exception messages are pretty informative, but special cases may miss a description and only report a path.
   */
  public static @NlsSafe @NotNull String message(@NotNull Throwable t) {
    var message = t.getMessage();

    if (t instanceof UncheckedIOException || t instanceof IOError) {
      t = t.getCause();
    }

    if (message == null || message.trim().isEmpty()) {
      return UIBundle.message("io.error.unknown");
    }

    if (t instanceof AccessDeniedException ade) {
      var reason = ade.getReason();
      if (reason != null) {
        return UIBundle.message("io.error.access.denied.reason", message, reason);
      }
      else {
        return UIBundle.message("io.error.access.denied", message);
      }
    }
    if (t instanceof DirectoryNotEmptyException) {
      return UIBundle.message("io.error.dir.not.empty", message);
    }
    if (t instanceof FileAlreadyExistsException) {
      return UIBundle.message("io.error.already.exists", message);
    }
    if (t instanceof NoSuchFileException) {
      return UIBundle.message("io.error.no.such.file", message);
    }
    if (t instanceof NotDirectoryException) {
      return UIBundle.message("io.error.not.dir", message);
    }
    if (t instanceof NotLinkException) {
      return UIBundle.message("io.error.not.link", message);
    }

    if (t instanceof FileSystemException && message.equals(((FileSystemException)t).getFile())) {
      return t.getClass().getSimpleName() + ": " + message;
    }

    return message;
  }
}
