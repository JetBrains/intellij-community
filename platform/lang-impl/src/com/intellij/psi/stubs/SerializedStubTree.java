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

import com.intellij.util.CompressionUtil;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class SerializedStubTree {
  private final byte[] myBytes;
  private final int myLength;
  private Stub myStubElement;

  public SerializedStubTree(final byte[] bytes, int length, @Nullable Stub stubElement) {
    myBytes = bytes;
    myLength = length;
    myStubElement = stubElement;
  }
  
  public SerializedStubTree(DataInput in) throws IOException {
    myBytes = CompressionUtil.readCompressed(in);
    myLength = myBytes.length;
  }

  public void write(DataOutput out) throws IOException {
    CompressionUtil.writeCompressed(out, myBytes, myLength);
  }

  // willIndexStub is one time optimization hint, once can safely pass false
  public Stub getStub(boolean willIndexStub) throws SerializerNotFoundException {
    Stub stubElement = myStubElement;
    if (stubElement != null) {
      // not null myStubElement means we just built SerializedStubTree for indexing,
      // if we request stub for indexing we can safely use it
      myStubElement = null;
      if (willIndexStub) return stubElement;
    }
    return SerializationManagerEx.getInstanceEx().deserialize(new UnsyncByteArrayInputStream(myBytes));
  }

  public boolean equals(final Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof SerializedStubTree)) {
      return false;
    }
    final SerializedStubTree thatTree = (SerializedStubTree)that;
    final int length = myLength;
    if (length != thatTree.myLength) {
      return false;
    }

    final byte[] thisBytes = myBytes;
    final byte[] thatBytes = thatTree.myBytes;
    for (int i=0; i< length; i++) {
      if (thisBytes[i] != thatBytes[i]) {
        return false;
      }
    }
    
    return true;
  }

  public int hashCode() {
    if (myBytes == null)
        return 0;

    int result = 1;
    for (int i = 0; i < myLength; i++) {
      result = 31 * result + myBytes[i];
    }

    return result;
  }

}