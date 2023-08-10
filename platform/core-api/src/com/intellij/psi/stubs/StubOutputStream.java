// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.util.io.AbstractStringEnumerator;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;


public class StubOutputStream extends DataOutputStream {
  private final AbstractStringEnumerator myNameStorage;

  public StubOutputStream(@NotNull OutputStream out, @NotNull AbstractStringEnumerator nameStorage) {
    super(out);
    myNameStorage = nameStorage;
  }

  public void writeUTFFast(final @NotNull String arg) throws IOException {
    IOUtil.writeUTF(this, arg);
  }

  public void writeName(final @Nullable String arg) throws IOException {
    final int nameId = arg != null ? myNameStorage.enumerate(arg) : 0;
    DataInputOutputUtil.writeINT(this, nameId);
  }

  public void writeVarInt(final int value) throws IOException {
    DataInputOutputUtil.writeINT(this, value);
  }

}
