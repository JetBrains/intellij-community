package com.intellij.diagnostic.errordialog;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Base64Converter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class Attachment {
  private final String myPath;
  private String myContent;
  private boolean myIncluded = true;

  public Attachment(String path, String content) {
    myPath = path;
    myContent = content;
  }

  public Attachment(@NotNull VirtualFile file) {
    this(file.getPresentableUrl(), loadText(file));
  }

  private static String loadText(VirtualFile file) {
    if (file.getFileType().isBinary()) {
      try {
        return "Binary file, base64 encoded: " + Base64Converter.encode(file.contentsToByteArray());
      }
      catch (IOException e) {
        return "Cannot load binary file content";
      }
    }
    else {
      return LoadTextUtil.loadText(file).toString();
    }
  }

  public String getPath() {
    return myPath;
  }

  public String getContent() {
    return myContent;
  }

  public void setContent(String content) {
    myContent = content;
  }

  public boolean isIncluded() {
    return myIncluded;
  }

  public void setIncluded(Boolean included) {
    myIncluded = included;
  }
}
