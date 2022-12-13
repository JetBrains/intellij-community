// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.UnsyncByteArrayBackedOutputStreamMarker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Experimental
public abstract class AttributeOutputStream extends DataOutputStream implements UnsyncByteArrayBackedOutputStreamMarker {
  public <T extends DataOutputStream & UnsyncByteArrayBackedOutputStreamMarker> AttributeOutputStream(T out) {
    super(out);
  }

  abstract public void writeEnumeratedString(String str) throws IOException;

  @Override
  public @NotNull ByteArraySequence getResultingBuffer() {
    return ((UnsyncByteArrayBackedOutputStreamMarker)out).getResultingBuffer();
  }
}