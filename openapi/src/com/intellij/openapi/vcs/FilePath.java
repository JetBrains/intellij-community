package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;

import java.io.File;
import java.nio.charset.Charset;

public interface FilePath {
  VirtualFile getVirtualFile();
  VirtualFile getVirtualFileParent();
  File getIOFile();

  String getName();

  String getPresentableUrl();

  Document getDocument();

  Charset getCharset();

  FileType getFileType();

  void refresh();

  String getPath();

  boolean isDirectory();
}
