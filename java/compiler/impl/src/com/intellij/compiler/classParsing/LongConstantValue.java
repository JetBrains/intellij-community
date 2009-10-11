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

/**
 * created at Feb 24, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class LongConstantValue extends ConstantValue{
  private final long myValue;

  public LongConstantValue(long value) {
    myValue = value;
  }
  public LongConstantValue(DataInput in) throws IOException{
    myValue = in.readLong();
  }

  public long getValue() {
    return myValue;
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    out.writeLong(myValue);
  }

  public boolean equals(Object obj) {
    return (obj instanceof LongConstantValue) && (((LongConstantValue)obj).myValue == myValue);
  }
}
