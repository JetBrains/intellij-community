// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.stubs;

import com.intellij.util.io.AbstractStringEnumerator;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;


public class StubInputStream extends DataInputStream {
  private final AbstractStringEnumerator myNameStorage;

  public StubInputStream(@NotNull InputStream in, @NotNull AbstractStringEnumerator nameStorage) {
    super(in);
    myNameStorage = nameStorage;
  }

  public @NotNull String readUTFFast() throws IOException {
    return IOUtil.readUTF(this);
  }

  public @Nullable StringRef readName() throws IOException {
    return StringRef.fromStream(this, myNameStorage);
  }

  public @Nullable String readNameString() throws IOException {
    return StringRef.stringFromStream(this, myNameStorage);
  }

  public int readVarInt() throws IOException {
    return DataInputOutputUtil.readINT(this);
  }

}
