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

public class EnumConstantValue extends ConstantValue{
  private final int myTypeName;
  private final int myConstantName;

  public EnumConstantValue(int typeName, int constantName) {
    myTypeName = typeName;
    myConstantName = constantName;
  }

  public EnumConstantValue(DataInput in) throws IOException{
    myTypeName = in.readInt();
    myConstantName = in.readInt();
  }

  public int getTypeName() {
    return myTypeName;
  }

  public int getConstantName() {
    return myConstantName;
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    out.writeInt(myTypeName);
    out.writeInt(myConstantName);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EnumConstantValue)) return false;

    final EnumConstantValue enumConstantValue = (EnumConstantValue)o;

    if (myConstantName != enumConstantValue.myConstantName) return false;
    if (myTypeName != enumConstantValue.myTypeName) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myTypeName;
    result = 29 * result + myConstantName;
    return result;
  }
}
