/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author yole
 */
public class StubOutputStream extends DataOutputStream {
  private final AbstractStringEnumerator myNameStorage;
  private final byte[] myStringIOBuffer = IOUtil.allocReadWriteUTFBuffer();

  public StubOutputStream(OutputStream out, AbstractStringEnumerator nameStorage) {
    super(out);
    myNameStorage = nameStorage;
  }

  public void writeUTFFast(@NotNull final String arg) throws IOException {
    IOUtil.writeUTFFast(myStringIOBuffer, this, arg);
  }

  public void writeName(@Nullable final String arg) throws IOException {
    DataInputOutputUtil.writeNAME(this, arg, myNameStorage);
  }

  public void writeVarInt(final int value) throws IOException {
    DataInputOutputUtil.writeINT(this, value);
  }

  public int getStringId(final String value) throws IOException {
    return myNameStorage.enumerate(value);
  }
}
