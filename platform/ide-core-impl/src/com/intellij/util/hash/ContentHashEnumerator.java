// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.hash;

import com.intellij.util.io.DifferentSerializableBytesImplyNonEqualityPolicy;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentBTreeEnumerator;
import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

@ApiStatus.Internal
public class ContentHashEnumerator extends PersistentBTreeEnumerator<byte[]> {

  private static final int SIGNATURE_LENGTH = 20; // in bytes

  public ContentHashEnumerator(@NotNull Path contentsHashesFile) throws IOException {
    this(contentsHashesFile, null);
  }

  public ContentHashEnumerator(@NotNull Path contentsHashesFile,
                               @Nullable StorageLockContext storageLockContext) throws IOException {
    this(contentsHashesFile, new ContentHashesDescriptor(), 64 * 1024, storageLockContext);
  }

  private ContentHashEnumerator(@NotNull Path file,
                                @NotNull KeyDescriptor<byte[]> dataDescriptor,
                                int initialSize,
                                @Nullable StorageLockContext lockContext) throws IOException {
    super(file, dataDescriptor, initialSize, lockContext);
    LOG.assertTrue(dataDescriptor instanceof DifferentSerializableBytesImplyNonEqualityPolicy);
  }

  public boolean hasHashFor(byte @NotNull [] value) throws IOException {
    return tryEnumerate(value) != NULL_ID;
  }

  @Override
  public int enumerate(byte @NotNull [] value) throws IOException {
    LOG.assertTrue(SIGNATURE_LENGTH == value.length);
    return super.enumerate(value);
  }

  @Override
  protected int doWriteData(byte[] value) throws IOException {
    return super.doWriteData(value) / SIGNATURE_LENGTH;
  }

  @Override
  public int getLargestId() {
    return super.getLargestId() / SIGNATURE_LENGTH;
  }

  @Override
  protected boolean isKeyAtIndex(byte[] value, int idx) throws IOException {
    return super.isKeyAtIndex(value, addrToIndex(indexToAddr(idx) * SIGNATURE_LENGTH));
  }

  @Override
  public byte[] valueOf(int idx) throws IOException {
    return super.valueOf(addrToIndex(indexToAddr(idx) * SIGNATURE_LENGTH));
  }

  public static int getVersion() {
    return PersistentBTreeEnumerator.baseVersion();
  }

  private static class ContentHashesDescriptor implements KeyDescriptor<byte[]>, DifferentSerializableBytesImplyNonEqualityPolicy {

    @Override
    public void save(@NotNull DataOutput out, byte[] value) throws IOException {
      out.write(value);
    }

    @Override
    public byte[] read(@NotNull DataInput in) throws IOException {
      byte[] b = new byte[SIGNATURE_LENGTH];
      in.readFully(b);
      return b;
    }

    @Override
    public int getHashCode(byte[] value) {
      int hash = 0; // take first 4 bytes, this should be good enough hash given we reference git revisions with 7-8 hex digits
      for (int i = 0; i < 4; ++i) {
        hash = (hash << 8) + (value[i] & 0xFF);
      }
      return hash;
    }

    @Override
    public boolean isEqual(byte[] val1, byte[] val2) {
      return Arrays.equals(val1, val2);
    }
  }
}
