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
package com.intellij.compiler.make;

import com.intellij.util.io.KeyDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 1, 2008
*/
class MethodIdKeyDescriptor implements KeyDescriptor<StorageMethodId> {
  public static final MethodIdKeyDescriptor INSTANCE = new MethodIdKeyDescriptor();

  public int getHashCode(StorageMethodId value) {
    return value.hashCode();
  }

  public boolean isEqual(StorageMethodId val1, StorageMethodId val2) {
    return val1.equals(val2);
  }

  public void save(DataOutput out, StorageMethodId value) throws IOException {
    out.writeInt(value.getClassQName());
    out.writeInt(value.getMethodName());
    out.writeInt(value.getMethodDescriptor());
  }

  public StorageMethodId read(DataInput in) throws IOException {
    final int qName = in.readInt();
    final int methodName = in.readInt();
    final int methodDescriptor = in.readInt();
    return new StorageMethodId(qName, methodName, methodDescriptor);
  }
}