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
class ClassIdKeyDescriptor implements KeyDescriptor<StorageClassId> {
  public static final ClassIdKeyDescriptor INSTANCE = new ClassIdKeyDescriptor();

  public int getHashCode(StorageClassId value) {
    return value.hashCode();
  }

  public boolean isEqual(StorageClassId val1, StorageClassId val2) {
    return val1.equals(val2);
  }

  public void save(DataOutput out, StorageClassId value) throws IOException {
    out.writeInt(value.getClassQName());
  }

  public StorageClassId read(DataInput in) throws IOException {
    return new StorageClassId(in.readInt());
  }
}