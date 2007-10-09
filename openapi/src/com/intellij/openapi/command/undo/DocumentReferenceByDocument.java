/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.command.undo;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author max
 */
public class DocumentReferenceByDocument extends DocumentReference {
  private Document myDocument;

  private DocumentReferenceByDocument(Document document) {
    myDocument = document;
  }

  public VirtualFile getFile() {
    return FileDocumentManager.getInstance().getFile(myDocument);
  }

  public Document getDocument() {
    return myDocument;
  }

  protected String getUrl() {
    VirtualFile file = getFile();
    if (file == null) return null;
    return file.getUrl();
  }

  public void beforeFileDeletion(VirtualFile file) {
  }

  public static DocumentReference createDocumentReference(Document document) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null) return new DocumentReferenceByVirtualFile(file);
    return new DocumentReferenceByDocument(document);
  }
}
