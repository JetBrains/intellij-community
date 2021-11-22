// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.ide.ui.IdeUiService;

/**
 * A marker interface for {@link VirtualFile#getOutputStream(Object)} to take extra caution w.r.t. an existing content.
 * Specifically, if the operation fails for whatever reason (like not enough disk space left), the prior content shall not be overwritten.
 *
 * @author max
 */
public interface SafeWriteRequestor {
  static boolean shouldUseSafeWrite(Object requestor) {
    return requestor instanceof SafeWriteRequestor && IdeUiService.getInstance().isUseSafeWrite();
  }
}
