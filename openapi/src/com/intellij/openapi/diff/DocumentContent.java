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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class DocumentContent extends DiffContent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.DocumentContent");
  private final Document myDocument;
  private final VirtualFile myFile;
  private final FileType myOverridenType;
  private Project myProject;

  public DocumentContent(Project project, Document document) {
    this(project, document, null);
  }

  public DocumentContent(Project project, Document document, FileType type) {
    myProject = project;
    LOG.assertTrue(document != null);
    myDocument = document;
    myFile = FileDocumentManager.getInstance().getFile(document);
    myOverridenType = type;
  }

  public Document getDocument() {
    return myDocument;
  }

  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    VirtualFile file = getFile();
    if (file == null) return null;
    return new OpenFileDescriptor(myProject, file, offset);
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public FileType getContentType() {
    return myOverridenType == null ? DiffContentUtil.getContentType(getFile()) : myOverridenType;
  }

  public byte[] getBytes() {
    return myDocument.getText().getBytes();
  }
}
