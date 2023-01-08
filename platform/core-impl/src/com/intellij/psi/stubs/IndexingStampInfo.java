/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.stubs;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An informational object for debugging stub-mismatch related issues. Should be as small as possible since it's stored in files's attributes.
 */
class IndexingStampInfo {
  final long indexingFileStamp;
  final long indexingByteLength;
  final int indexingCharLength;
  final boolean isBinary;

  IndexingStampInfo(long indexingFileStamp, long indexingByteLength, int indexingCharLength, boolean isBinary) {
    this.indexingFileStamp = indexingFileStamp;
    this.indexingByteLength = indexingByteLength;
    this.indexingCharLength = indexingCharLength;
    this.isBinary = isBinary;
  }

  @Override
  public String toString() {
    return "indexing timestamp = " + indexingFileStamp + ", " +
           "binary = " + isBinary + ", byte size = " + indexingByteLength + ", char size = " + indexingCharLength;
  }

  public boolean isUpToDate(@Nullable Document document, @NotNull VirtualFile file, @NotNull PsiFile psi) {
    if (document == null ||
        FileDocumentManager.getInstance().isDocumentUnsaved(document) ||
        !PsiDocumentManager.getInstance(psi.getProject()).isCommitted(document)) {
      return false;
    }

    boolean isFileBinary = file.getFileType().isBinary();
    return indexingFileStamp == file.getTimeStamp() &&
           isBinary == isFileBinary &&
           contentLengthMatches(file.getLength(), document.getTextLength());
  }

  public boolean contentLengthMatches(long byteContentLength, int charContentLength) {
    if (this.indexingCharLength >= 0 && charContentLength >= 0) {
      return this.indexingCharLength == charContentLength;
    }
    //
    // Due to VFS implementation reasons we cannot guarantee file.getLength() and VFS events consistency.
    // In this case we prefer to skip this check and leave `indexingByteLength` value only for informational reasons.
    //
    return true; //this.indexingByteLength == byteContentLength;
  }
}
