// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author yole
 */
public final class AttachmentFactory {
  private static final Logger LOG = Logger.getInstance(AttachmentFactory.class);

  private static final long BIG_FILE_THRESHOLD_BYTES = 50 * 1024;

  @NotNull
  public static Attachment createContext(@NotNull Object start, Object... more) {
    StringBuilder builder = new StringBuilder(String.valueOf(start));
    for (Object o : more) builder.append(",").append(o);
    return new Attachment("current-context.txt", builder.length() > 0 ? builder.toString() : "(unknown)");
  }

  @NotNull
  public static Attachment createAttachment(@NotNull Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return new Attachment(file != null ? file.getPath() : "unknown.txt", document.getText());
  }

  @NotNull
  public static Attachment createAttachment(@NotNull VirtualFile file) {
    return createAttachment(file.getPresentableUrl(), file);
  }

  @NotNull
  public static Attachment createAttachment(@NotNull String path, @NotNull VirtualFile file) {
    try (InputStream inputStream = file.getInputStream()) {
      return createAttachment(path, inputStream, file.getLength(), file.getFileType().isBinary());
    }
    catch (IOException e) {
      return handleException(e, file.getPath());
    }
  }

  @NotNull
  public static Attachment createAttachment(@NotNull File file, boolean isBinary) {
    return createAttachment(file.toPath(), isBinary);
  }

  @NotNull
  public static Attachment createAttachment(@NotNull Path file, boolean isBinary) {
    try (InputStream inputStream = Files.newInputStream(file)) {
      return createAttachment(file.toString(), inputStream, Files.size(file), isBinary);
    }
    catch (IOException e) {
      return handleException(e, file.toString());
    }
  }

  private static Attachment handleException(Throwable t, String path) {
    LOG.warn("failed to create an attachment from " + path, t);
    return new Attachment(path, t);
  }

  private static Attachment createAttachment(String path, InputStream content, long contentLength, boolean isBinary) throws IOException {
    if (contentLength >= BIG_FILE_THRESHOLD_BYTES) {
      File tempFile = FileUtil.createTempFile("ij-attachment-" + PathUtilRt.getFileName(path) + ".", isBinary ? ".bin" : ".txt", true);
      try (OutputStream outputStream = new FileOutputStream(tempFile)) {
        FileUtil.copy(content, contentLength, outputStream);
      }
      return new Attachment(path, tempFile, "[File is too big to display]");
    }
    else {
      byte[] bytes = FileUtil.loadBytes(content);
      String displayText = isBinary ? "[File is binary]" : new String(bytes, StandardCharsets.UTF_8);
      return new Attachment(path, bytes, displayText);
    }
  }
}