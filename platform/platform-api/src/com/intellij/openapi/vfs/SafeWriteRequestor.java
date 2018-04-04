// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.ide.GeneralSettings;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A marker interface for {@link VirtualFile#getOutputStream(Object)} to take extra caution overwriting existing content.
 * Specifically, if the operation fails for certain reason (like not enough disk space left) prior content shall not be overwritten (partially).
 *
 * @author max
 */
public interface SafeWriteRequestor {
  static boolean shallUseSafeStream(Object requestor, @NotNull VirtualFile file) {
    return requestor instanceof SafeWriteRequestor && GeneralSettings.getInstance().isUseSafeWrite() && !file.is(VFileProperty.SYMLINK);
  }

  static boolean shallUseSafeStream(Object requestor, @NotNull Path file) {
    return requestor instanceof SafeWriteRequestor && GeneralSettings.getInstance().isUseSafeWrite() && !Files.isSymbolicLink(file);
  }
}