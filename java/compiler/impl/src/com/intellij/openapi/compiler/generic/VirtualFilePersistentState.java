/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.compiler.generic;

import com.intellij.util.io.DataExternalizer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
* @author nik
*/
public class VirtualFilePersistentState {
  public static DataExternalizer<VirtualFilePersistentState> EXTERNALIZER = new VirtualFileStateExternalizer();
  private final long mySourceTimestamp;

  public VirtualFilePersistentState(long sourceTimestamp) {
    mySourceTimestamp = sourceTimestamp;
  }

  public final long getSourceTimestamp() {
    return mySourceTimestamp;
  }

  private static class VirtualFileStateExternalizer implements DataExternalizer<VirtualFilePersistentState> {
    @Override
    public void save(DataOutput out, VirtualFilePersistentState value) throws IOException {
      out.writeLong(value.getSourceTimestamp());
    }

    @Override
    public VirtualFilePersistentState read(DataInput in) throws IOException {
      return new VirtualFilePersistentState(in.readLong());
    }

  }
}
