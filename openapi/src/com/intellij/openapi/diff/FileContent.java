/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diff;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class FileContent extends DiffContent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.FileContent");
  private final VirtualFile myFile;
  private Document myDocument;
  private Project myProject;

  public FileContent(Project project, VirtualFile file) {
    myProject = project;
    LOG.assertTrue(file != null);
    myFile = file;
  }

  public Document getDocument() {
    if (myDocument == null && DiffContentUtil.isTextFile(myFile))
      myDocument = FileDocumentManager.getInstance().getDocument(myFile);
    return myDocument;
  }

  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    return new OpenFileDescriptor(myProject, myFile, offset);
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public FileType getContentType() {
    return DiffContentUtil.getContentType(myFile);
  }

  public byte[] getBytes() throws IOException {
    if (myFile.isDirectory()) return null;
    return myFile.contentsToByteArray();
  }

  public boolean isBinary() {
    if (myFile.isDirectory()) return false;
    return FileTypeManager.getInstance().getFileTypeByFile(myFile).isBinary();
  }
}
