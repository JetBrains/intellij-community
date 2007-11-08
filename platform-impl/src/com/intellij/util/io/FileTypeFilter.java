package com.intellij.util.io;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;

import javax.swing.filechooser.FileFilter;
import java.io.File;

public class FileTypeFilter extends FileFilter {
  private FileType myType;

  public FileTypeFilter(FileType fileType) {
    myType = fileType;
    myDescription = myType.getDescription();
  }

  public boolean accept(File f) {
    if (f.isDirectory()) return true;
    FileType type = FileTypeManager.getInstance().getFileTypeByFileName(f.getName());
    return myType == type;
  }

  public String getDescription() {
    return myDescription;
  }

  private String myDescription;
}
