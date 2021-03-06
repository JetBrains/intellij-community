// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to overwrite project level UTF-8 BOM option for a specific virtual file.
 */
public interface Utf8BomOptionProvider {
  ExtensionPointName<Utf8BomOptionProvider> EP_NAME = new ExtensionPointName<>("com.intellij.utf8BomOptionProvider");

  /**
   * @param file The file to check.
   * @return true if BOM should be added for UTF-8-encoded file.
   * @see EncodingManager#shouldAddBOMForNewUtf8File()
   */
  boolean shouldAddBOMForNewUtf8File(@NotNull VirtualFile file);
}
