/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author yole
 */
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
