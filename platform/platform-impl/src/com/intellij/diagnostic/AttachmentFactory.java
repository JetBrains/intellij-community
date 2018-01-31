package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.text.MessageFormat;

/**
 * @author yole
 */
public class AttachmentFactory {
  private static final Logger LOG = Logger.getInstance(AttachmentFactory.class);

  private static final Long BIG_FILE_THRESHOLD_BYTES = 50 * 1024L;

  private static final String ERROR_MESSAGE_PATTERN = "[[[Can't get file contents: {0}]]]";
  private static final String BIG_FILE_MESSAGE_PATTERN = "[[[File is too big to display: {0}]]]";

  public static Attachment createAttachment(Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return new Attachment(file != null ? file.getPath() : "unknown.txt", document.getText());
  }

  public static Attachment createAttachment(@NotNull VirtualFile file) {
    try {
      boolean isBinary = file.getFileType().isBinary();
      boolean isBigFile = file.getLength() > BIG_FILE_THRESHOLD_BYTES;

      try (InputStream inputStream = file.getInputStream()) {
        return createAttachment(file.getPresentableUrl(), inputStream, isBinary, isBigFile);
      }
    } catch (IOException e) {
      final String errorMessage = MessageFormat.format(ERROR_MESSAGE_PATTERN, e.getMessage());

      LOG.warn("Unable to create Attachment from " + file + ": " + e.getMessage(), e);
      return new Attachment(file.getPath(), errorMessage);
    }
  }

  public static Attachment createAttachment(@NotNull File file, boolean isBinary) {
    try {
      try (InputStream inputStream = new FileInputStream(file)) {
        return createAttachment(file.getPath(), inputStream, isBinary, file.length() > BIG_FILE_THRESHOLD_BYTES);
      }
    } catch (IOException e) {
      final String errorMessage = MessageFormat.format(ERROR_MESSAGE_PATTERN, e.getMessage());

      LOG.warn("Unable to create Attachment from " + file + ": " + e.getMessage(), e);
      return new Attachment(file.getPath(), errorMessage);
    }
  }

  private static Attachment createAttachment(@NotNull String path, InputStream contentStream, boolean isBinary, boolean isBigFile) {
    if (isBigFile) {
      return new Attachment(path, contentStream, MessageFormat.format(BIG_FILE_MESSAGE_PATTERN, path));
    } else {
      byte[] bytes = getBytes(contentStream);
      final String displayText = isBinary ? "[File is binary]" : getText(bytes);
      return new Attachment(path, bytes, displayText);
    }
  }

  private static String getText(byte[] bytes) {
    try {
      return new String(bytes, CharsetToolkit.UTF8_CHARSET);
    } catch (Throwable t) {
      return "[Binary content]";
    }
  }

  private static byte[] getBytes(InputStream inputStream) {
    try {
      return FileUtil.loadBytes(inputStream);
    }
    catch (IOException e) {
      return MessageFormat.format(ERROR_MESSAGE_PATTERN, e.getMessage()).getBytes(CharsetToolkit.UTF8_CHARSET);
    }
  }
}
