// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;

public class DocumentContent extends DiffContent {
  private final Document myDocument;
  private final VirtualFile myFile;
  private final FileType myOverriddenType;
  private final Project myProject;
  private final FileDocumentManager myDocumentManager;

  public DocumentContent(Project project, Document document) {
    this(project, document, null);
  }

  public DocumentContent(Project project, @NotNull Document document, FileType type) {
    myProject = project;
    myDocument = document;
    myDocumentManager = FileDocumentManager.getInstance();
    myFile = myDocumentManager.getFile(document);
    myOverriddenType = type;
  }

  public DocumentContent(@NotNull Document document) {
    this(null, document, null);
  }

  public DocumentContent(@NotNull Document document, @NotNull FileType type) {
    this(null, document, type);
  }

  @Override
  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @Override
  public Navigatable getOpenFileDescriptor(int offset) {
    VirtualFile file = getFile();
    if (file == null) return null;
    if (myProject == null) return null;
    return PsiNavigationSupport.getInstance().createNavigatable(myProject, file, offset);
  }

  @Override
  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  @Nullable
  public FileType getContentType() {
    return myOverriddenType == null ? DiffContentUtil.getContentType(getFile()) : myOverriddenType;
  }

  @Override
  public byte[] getBytes() {
    return myDocument.getText().getBytes(StandardCharsets.UTF_8);
  }

  @NotNull
  @Override
  public LineSeparator getLineSeparator() {
    return LineSeparator.fromString(myDocumentManager.getLineSeparator(myFile, myProject));
  }
}
