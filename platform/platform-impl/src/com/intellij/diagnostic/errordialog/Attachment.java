package com.intellij.diagnostic.errordialog;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Base64Converter;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;

public class Attachment {

  private static final String ERROR_MESSAGE_PATTERN = "[[[Can't get file contents: {0}]]]";

  private final String myPath;
  private final byte[] myBytes;
  private boolean myIncluded = true;
  private final String myDisplayText;

  public Attachment(String path, String content) {
    myPath = path;
    myDisplayText = content;
    myBytes = getBytes(content);
  }

  public Attachment(Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    myPath = file != null ? file.getPath() : "unknown.txt";
    myDisplayText = document.getText();
    myBytes = getBytes(myDisplayText);
  }

  public Attachment(@NotNull VirtualFile file) {
    myPath = file.getPresentableUrl();
    myBytes = getBytes(file);
    myDisplayText = file.getFileType().isBinary() ? "File is binary" : LoadTextUtil.loadText(file).toString();
  }

  private static byte[] getBytes(VirtualFile file) {
    try {
      return file.contentsToByteArray();
    }
    catch (IOException e) {
      return getBytes(MessageFormat.format(ERROR_MESSAGE_PATTERN, e.getMessage()));
    }
  }

  private static byte[] getBytes(String content) {
    try {
      return content.getBytes("UTF-8");
    }
    catch (UnsupportedEncodingException ignored) {
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }
  }

  public String getDisplayText() {
    return myDisplayText;
  }

  public String getPath() {
    return myPath;
  }

  public String getName() {
    return PathUtil.getFileName(myPath);
  }

  public String getEncodedBytes() {
    return Base64Converter.encode(myBytes);
  }

  public boolean isIncluded() {
    return myIncluded;
  }

  public void setIncluded(Boolean included) {
    myIncluded = included;
  }
}
