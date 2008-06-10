package com.intellij.ide.highlighter;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.extensions.ExtensionPointName;

public interface FileTypeRegistrator {
  ExtensionPointName<FileTypeRegistrator> EP_NAME = ExtensionPointName.create("com.intellij.fileTypeRegistrator");

  void initFileType(FileType fileType);
}
