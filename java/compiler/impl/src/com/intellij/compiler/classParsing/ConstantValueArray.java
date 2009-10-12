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
package com.intellij.compiler.classParsing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 2, 2004
 */
public class ConstantValueArray extends ConstantValue{
  private final ConstantValue[] myValue;

  public ConstantValueArray(ConstantValue[] value) {
    myValue = value;
  }

  public ConstantValueArray(DataInput in) throws IOException {
    final int size = in.readInt();
    myValue = new ConstantValue[size];
    for (int idx = 0; idx < size; idx++) {
      myValue[idx] = MemberInfoExternalizer.loadConstantValue(in);
    }
  }

  public ConstantValue[] getValue() {
    return myValue;
  }

  public void save(DataOutput out) throws IOException {
    out.writeInt(myValue.length);
    for (int idx = 0; idx < myValue.length; idx++) {
      MemberInfoExternalizer.saveConstantValue(out, myValue[idx]);
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ConstantValueArray)) return false;

    final ConstantValueArray constantValueArray = (ConstantValueArray)o;

    if (!Arrays.equals(myValue, constantValueArray.myValue)) return false;

    return true;
  }

  public int hashCode() {
    return 0;
  }

}
