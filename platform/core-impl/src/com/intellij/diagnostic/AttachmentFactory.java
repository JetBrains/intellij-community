// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link CoreAttachmentFactory} or {@link com.intellij.openapi.diagnostic.AttachmentFactory} instead.
 */
@Deprecated
public final class AttachmentFactory {
  /**
   * @deprecated use {@link CoreAttachmentFactory#createAttachment(VirtualFile)} instead
   */
  @Deprecated
  public static @NotNull Attachment createAttachment(@NotNull VirtualFile file) {
    return CoreAttachmentFactory.createAttachment(file);
  }
}
