// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * @author yole
 */
public class AttachmentFactory {
  private static final Logger LOG = Logger.getInstance(AttachmentFactory.class);

  private static final long BIG_FILE_THRESHOLD_BYTES = 50 * 1024;

  public static Attachment createContext(@NotNull Object start, Object... more) {
    StringBuilder builder = new StringBuilder(String.valueOf(start));
    for (Object o : more) builder.append(",").append(o);
    return new Attachment("current-context.txt", builder.length() > 0 ? builder.toString() : "(unknown)");
  }

  public static Attachment createAttachment(@NotNull Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return new Attachment(file != null ? file.getPath() : "unknown.txt", document.getText());
  }

  public static Attachment createAttachment(@NotNull VirtualFile file) {
    try (InputStream inputStream = file.getInputStream()) {
      return createAttachment(file.getPresentableUrl(), inputStream, file.getLength(), file.getFileType().isBinary());
    }
    catch (IOException e) {
      return handleException(e, file.getPath());
    }
  }

  public static Attachment createAttachment(@NotNull File file, boolean isBinary) {
    try (InputStream inputStream = new FileInputStream(file)) {
      return createAttachment(file.getPath(), inputStream, file.length(), isBinary);
    }
    catch (IOException e) {
      return handleException(e, file.getPath());
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
      String displayText = isBinary ? "[File is binary]" : new String(bytes, CharsetToolkit.UTF8_CHARSET);
      return new Attachment(path, bytes, displayText);
    }
  }
}