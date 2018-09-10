/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    return myDocument.getText().getBytes();
  }

  @NotNull
  @Override
  public LineSeparator getLineSeparator() {
    return LineSeparator.fromString(myDocumentManager.getLineSeparator(myFile, myProject));
  }
}
