// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.stubs;

import com.intellij.util.io.*;
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

  @NotNull 
  public String readUTFFast() throws IOException {
    return IOUtil.readUTF(this);
  }

  @Nullable 
  public StringRef readName() throws IOException {
    return StringRef.fromStream(this, myNameStorage);
  }

  @Nullable 
  public String readNameString() throws IOException {
    return StringRef.stringFromStream(this, myNameStorage);
  }

  public int readVarInt() throws IOException {
    return DataInputOutputUtil.readINT(this);
  }

}
