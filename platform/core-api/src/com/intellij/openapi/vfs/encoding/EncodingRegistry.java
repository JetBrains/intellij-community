// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author yole
 */
public abstract class EncodingRegistry {
  public abstract boolean isNative2Ascii(@NotNull VirtualFile virtualFile);
  public abstract boolean isNative2AsciiForPropertiesFiles();

  /**
   * @return charset configured in Settings|File Encodings|IDE encoding
   */
  @NotNull
  public abstract Charset getDefaultCharset();

  /**
   * @param virtualFile  file to get encoding for
   * @param useParentDefaults true to determine encoding from the parent
   * @return encoding configured for this file in Settings|File Encodings or,
   *         if useParentDefaults is true, encoding configured for nearest parent of virtualFile or,
   *         null if there is no configured encoding found.
   */
  @Nullable
  public abstract Charset getEncoding(@Nullable VirtualFile virtualFile, boolean useParentDefaults);

  public abstract void setEncoding(@Nullable("null means project") VirtualFile virtualFileOrDir, @Nullable("null means remove mapping") Charset charset);

  @Nullable("null means 'use system-default'")
  public Charset getDefaultCharsetForPropertiesFiles(@Nullable VirtualFile virtualFile) {
    return null;
  }

  public static EncodingRegistry getInstance() {
    return EncodingManager.getInstance();
  }

  public static <E extends Throwable> VirtualFile doActionAndRestoreEncoding(@NotNull VirtualFile fileBefore,
                                                                             @NotNull ThrowableComputable<? extends VirtualFile, E> action) throws E {
    EncodingRegistry registry = getInstance();
    Charset charsetBefore = registry.getEncoding(fileBefore, true);
    VirtualFile fileAfter = null;
    try {
      fileAfter = action.compute();
      return fileAfter;
    }
    finally {
      if (fileAfter != null) {
        Charset actual = registry.getEncoding(fileAfter, true);
        if (!Comparing.equal(actual, charsetBefore)) {
          registry.setEncoding(fileAfter, charsetBefore);
        }
      }
    }
  }
}
