package com.intellij.compiler.impl;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;

/**
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 *
 * @author Eugene Zhuravlev
 *         Date: May 5, 2004
 */
public class CompilerContentIterator implements ContentIterator {
  final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
  private final FileType myFileType;
  private final FileIndex myFileIndex;
  private final boolean myInSourceOnly;
  private final Collection<VirtualFile> myFiles;

  public CompilerContentIterator(FileType fileType, FileIndex fileIndex, boolean inSourceOnly, Collection<VirtualFile> files) {
    myFileType = fileType;
    myFileIndex = fileIndex;
    myInSourceOnly = inSourceOnly;
    myFiles = files;
  }

  public boolean processFile(VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) return true;
    if (!fileOrDir.isInLocalFileSystem()) return true;
    if (myInSourceOnly && !myFileIndex.isInSourceContent(fileOrDir)) return true;
    if (myFileType == null || myFileType == fileTypeManager.getFileTypeByFile(fileOrDir)) {
      myFiles.add(fileOrDir);
    }
    return true;
  }
}
