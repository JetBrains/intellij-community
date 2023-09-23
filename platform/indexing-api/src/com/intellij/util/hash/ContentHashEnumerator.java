// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.hash;

import com.intellij.util.io.DurableDataEnumerator;
import com.intellij.util.io.ScannableDataEnumeratorEx;
import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Enumerates content hashes (=cryptographic hashes of a file content)
 * We use {@link #SIGNATURE_LENGTH}-long hashes, and we really don't care about cryptographic strength of a hash
 * -- we use it for the same purposes git does.
 * Content hashes enumerator must assign hashes with <b>consequent</b> ids  -- this is a strengthening to generic
 * {@link com.intellij.util.io.DataEnumerator} contract (which doesn't specify how to generate an id).
 *
 */
public interface ContentHashEnumerator extends DurableDataEnumerator<byte[]>,
                                               ScannableDataEnumeratorEx<byte[]> {

  /** Length of hash byte-array. ContentHashEnumerator fails to accept hashes.length != SIGNATURE_LENGTH */
  int SIGNATURE_LENGTH = 20;

  static int getVersion() {
    return ContentHashEnumeratorOverBTree.getVersion();
  }

  static ContentHashEnumerator open(@NotNull Path storagePath) throws IOException {
    return new ContentHashEnumeratorOverBTree(storagePath);
  }

  static ContentHashEnumerator open(@NotNull Path storagePath,
                                    @NotNull StorageLockContext context) throws IOException {
    return new ContentHashEnumeratorOverBTree(storagePath, context);
  }

  /** @return _consequential_ id, starting with 1 */
  @Override
  int enumerate(byte @NotNull [] hash) throws IOException;

  /** @return positive id if the hash was new to enumerator (i.e. first time), -id if a hash was already known */
  int enumerateEx(byte @NotNull [] hash) throws IOException;

  @Override
  byte[] valueOf(int idx) throws IOException;
}
