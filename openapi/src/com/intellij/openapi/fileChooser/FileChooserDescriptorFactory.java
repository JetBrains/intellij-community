/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;

public class FileChooserDescriptorFactory {

  public static FileChooserDescriptor createAllButJarContentsDescriptor() {
    return new FileChooserDescriptor(true, true, true, true, false, true);
  }

  public static FileChooserDescriptor createMultipleFilesNoJarsDescriptor() {
    return new FileChooserDescriptor(true, false, false, false, false, true);
  }

  public static FileChooserDescriptor createMultipleFoldersDescriptor() {
    return new FileChooserDescriptor(false, true, false, false, false, true);
  }

  public static FileChooserDescriptor createSingleFileNoJarsDescriptor() {
    return new FileChooserDescriptor(true, false, false, false, false, false);
  }

  public static FileChooserDescriptor createSingleLocalFileDescriptor() {
    return new FileChooserDescriptor(true, true, true, true, false, false);
  }

  public static FileChooserDescriptor createXmlDescriptor() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, true){
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        boolean b = super.isFileVisible(file, showHiddenFiles);
        if (!file.isDirectory()) {
          b &= StdFileTypes.XML.equals(FileTypeManager.getInstance().getFileTypeByFile(file));
        }
        return b;
      }
    };
    return descriptor;
  }

  public static FileChooserDescriptor createSingleFolderDescriptor() {
    return new FileChooserDescriptor(false, true, false, false, false, false);
  }

  public static FileChooserDescriptor createMultipleJavaPathDescriptor() {
    return new FileChooserDescriptor(false, true, true, false, true, true);
  }

  public static FileChooserDescriptor getDirectoryChooserDescriptor(String aSearchedObjectName) {
    final FileChooserDescriptor singleFolderDescriptor = createSingleFolderDescriptor();
    singleFolderDescriptor.setTitle("Select " + aSearchedObjectName);
    return singleFolderDescriptor;
  }

  public static FileChooserDescriptor getFileChooserDescriptor(String aSearchedObjectName) {
    final FileChooserDescriptor fileChooserDescriptor = createSingleFileNoJarsDescriptor();
    fileChooserDescriptor.setTitle("Select " + aSearchedObjectName);
    return fileChooserDescriptor;
  }
}
