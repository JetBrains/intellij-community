// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

public final class CoreAttachmentFactory {
  private static final Logger LOG = Logger.getInstance(CoreAttachmentFactory.class);
  
  public static @NotNull Attachment createAttachment(@NotNull Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return new Attachment(file != null ? file.getPath() : "unknown.txt", document.getText());
  }

  public static @NotNull Attachment createAttachment(@NotNull VirtualFile file) {
    return createAttachment(file.getPresentableUrl(), file);
  }

  public static @NotNull Attachment createAttachment(@NotNull String path, @NotNull VirtualFile file) {
    try (InputStream inputStream = file.getInputStream()) {
      return com.intellij.openapi.diagnostic.AttachmentFactory.createAttachment(path, inputStream, file.getLength(), file.getFileType().isBinary());
    }
    catch (IOException e) {
      LOG.warn("failed to create an attachment from " + file.getPath(), e);
      return new Attachment(file.getPath(), e);
    }
  }
}
