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

/*
 * @author max
 */
package com.intellij.psi.stubs;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

public class SerializedStubTree {
  private final byte[] myBytes;
  private final StubElement myStub;

  public SerializedStubTree(final byte[] bytes, @Nullable StubElement stub) {
    myBytes = bytes;
    myStub = stub;
  }

  public StubElement getStub() {
    return myStub == null ? SerializationManager.getInstance().deserialize(new ByteArrayInputStream(myBytes)) : myStub;
  }

  public boolean equals(final Object that) {
    return this == that ||
           that instanceof SerializedStubTree && Arrays.equals(myBytes, ((SerializedStubTree)that).myBytes);
  }

  public int hashCode() {
    return Arrays.hashCode(myBytes);
  }

  public byte[] getBytes() {
    return myBytes;
  }
}