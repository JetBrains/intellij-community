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

import static java.lang.Math.max;
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

  public int[] toInt3() {
    // 48 bits for indexingFileStamp
    // 16 bits for indexingCharLengthDiff (indexingCharLength = indexingByteLength + indexingCharLengthDiff)
    // 1 bit for binary flag
    // 31 bits for indexingByteLength, unsigned (files >2GB are represented with 2^31-1)

    int representableByteLength = coerceToNonNegativeInt(indexingByteLength);
    int binary = isBinary ? 0x80_00_00_00 : 0;
    short indexingCharLengthDiff = coerceToShort(indexingCharLength - representableByteLength);
    int[] ints = new int[3];
    ints[0] = (int)(indexingFileStamp & 0x0_ff_ff_ff_ffL);
    ints[1] = (int)(((indexingFileStamp >> 32) & 0x0_ff_ff) | (indexingCharLengthDiff << 16));
    ints[2] = representableByteLength | binary;
    return ints;
  }

  public static IndexingStampInfo fromInt3(int[] ints) {
    assert ints.length == 3 : Arrays.toString(ints);
    long indexingFileStamp = (ints[0] & 0x0_ff_ff_ff_ffL) | ((long)(ints[1] & 0x0_ff_ff) << 32);
    int indexingCharLengthDiff = (ints[1] >> 16);
    boolean isBinary = ((ints[2] & 0x80_00_00_00) != 0);
    long indexingByteLength = (ints[2] & ~0x80_00_00_00);
    int indexingCharLength = (int)(indexingByteLength + indexingCharLengthDiff);

    return new IndexingStampInfo(indexingFileStamp, indexingByteLength, isBinary ? -1 : indexingCharLength, isBinary);
  }

  private static short coerceToShort(int l) {
    return (short)max(Short.MIN_VALUE, min(l, Short.MAX_VALUE));
  }

  private static int coerceToNonNegativeInt(long l) {
    return (int)max(0, min(l, Integer.MAX_VALUE));
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
    int result = (int)(indexingFileStamp ^ (indexingFileStamp >>> 32));
    result = 31 * result + (int)(indexingByteLength ^ (indexingByteLength >>> 32));
    result = 31 * result + indexingCharLength;
    result = 31 * result + (isBinary ? 1 : 0);
    return result;
  }
}
