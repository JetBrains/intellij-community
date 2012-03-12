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

import com.intellij.util.io.UnsyncByteArrayInputStream;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class SerializedStubTree {
  private final byte[] myBytes;
  private final int myLength;

  public SerializedStubTree(final byte[] bytes, int length) {
    myBytes = bytes;
    myLength = length;
  }
  
  public SerializedStubTree(DataInput in) throws IOException {
    myLength = in.readInt();
    myBytes = new byte[myLength];
    in.readFully(myBytes);
  }

  public void write(DataOutput out) throws IOException{
    out.writeInt(myLength);
    out.write(myBytes, 0, myLength);
  }

  public StubElement getStub() {
    return SerializationManager.getInstance().deserialize(new UnsyncByteArrayInputStream(myBytes));
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