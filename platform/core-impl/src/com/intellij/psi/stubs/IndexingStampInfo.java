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

import java.util.Arrays;

import static java.lang.Math.min;

/**
 * An informational object for debugging stub-mismatch related issues. Should be as small as possible since it's stored in files's attributes.
 */
class IndexingStampInfo {
  final long indexingFileStamp;
  final long indexingByteLength;
  final int indexingCharLength;
  final boolean isBinary;

  IndexingStampInfo(long indexingFileStamp, long indexingByteLength, int indexingCharLength, boolean isBinary) {
    assert (indexingByteLength >= 0) : this.toString();
    assert (indexingCharLength >= 0 || (indexingCharLength == -1 && isBinary)) : this.toString();

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

  public int[] toInt4() {
    // 64 bits for indexingFileStamp (48 is enough in most cases, if you want to save 16 bits)
    // 1 bit for binary flag
    // 31 bits for indexingCharLength, unsigned
    // 32 bits for indexingByteLength, signed for simplicity (files >2GB are represented with 2^31-1)

    // we don't actually use indexingByteLength for diagnostics (see contentLengthMatches), so we can reduce stored size by 1 int,
    // still it is useful for debugging, until we find a better way to utilize this space (we can only store 2 or 4 ints per file, not 3)

    int binary = isBinary ? 0x80_00_00_00 : 0;
    int[] ints = new int[4];
    ints[0] = (int)(indexingFileStamp & 0x0_ff_ff_ff_ffL);
    ints[1] = (int)((indexingFileStamp >> 32) & 0x0_ff_ff_ff_ffL);
    ints[2] = indexingCharLength | binary;
    ints[3] = (int)min(indexingByteLength, Integer.MAX_VALUE);
    return ints;
  }

  public static IndexingStampInfo fromInt4(int[] ints) {
    assert ints.length == 4 : Arrays.toString(ints);
    long indexingFileStamp = (ints[0] & 0x0_ff_ff_ff_ffL) | ((long)ints[1] << 32);
    boolean isBinary = ((ints[2] & 0x80_00_00_00) != 0);
    int indexingCharLength = (ints[2] & ~0x80_00_00_00);
    int indexingByteLength = ints[3];

    return new IndexingStampInfo(indexingFileStamp, indexingByteLength, isBinary ? -1 : indexingCharLength, isBinary);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IndexingStampInfo info = (IndexingStampInfo)o;

    if (indexingFileStamp != info.indexingFileStamp) return false;
    if (indexingByteLength != info.indexingByteLength) return false;
    if (indexingCharLength != info.indexingCharLength) return false;
    if (isBinary != info.isBinary) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = Long.hashCode(indexingFileStamp);
    result = 31 * result + Long.hashCode(indexingByteLength);
    result = 31 * result + indexingCharLength;
    result = 31 * result + (isBinary ? 1 : 0);
    return result;
  }
}
