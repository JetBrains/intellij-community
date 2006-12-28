/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author max
 */
public class CurrentBinaryContentRevision implements BinaryContentRevision {
  private FilePath myFile;

  public CurrentBinaryContentRevision(final FilePath file) {
    myFile = file;
  }

  @Nullable
  public byte[] getBinaryContent() throws VcsException {
    final VirtualFile vFile = getVirtualFile();
    if (vFile == null) return null;
    try {
      return vFile.contentsToByteArray();
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    final VirtualFile vFile = myFile.getVirtualFile();
    if (vFile == null || !vFile.isValid()) return null;
    return vFile;
  }

  @NotNull
  public FilePath getFile() {
    return myFile;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return VcsRevisionNumber.NULL;
  }

  @Nullable
  public String getContent() throws VcsException {
    final VirtualFile vFile = getVirtualFile();
    if (vFile == null) return null;
    final Document doc = FileDocumentManager.getInstance().getDocument(vFile);
    return doc.getText();
  }
}