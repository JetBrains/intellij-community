/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author max
 */
public abstract class DocumentReference {
  public static DocumentReference[] EMPTY_ARRAY = new DocumentReference[0];
  public int hashCode() {
    VirtualFile file = getFile();
    return file != null ? file.hashCode() : getDocument().hashCode();
  }

  public boolean equals(Object object) {
    if (!(object instanceof DocumentReference)) return false;
    VirtualFile file1 = getFile();
    VirtualFile file2 = ((DocumentReference) object).getFile();
    if (file1 != null) return file1.equals(file2);
    if (file2 != null) return file2.equals(file1);

    return getDocument().equals(((DocumentReference) object).getDocument());
  }

  public abstract VirtualFile getFile();
  public abstract Document getDocument();
  public abstract void beforeFileDeletion(VirtualFile file);

  protected abstract String getUrl();

  public boolean equalsByUrl(String url) {
    VirtualFile file = getFile();
    if (file == null) return false;
    if (file.isValid()) return false;
    String url1 = getUrl();
    if ((url1 == null) || (url == null)) return false;
    if (SystemInfo.isFileSystemCaseSensitive)
      return url.equals(url1);
    else
      return url.compareToIgnoreCase(url1) == 0;
  }

  public String toString() {
    return getUrl();
  }
}
