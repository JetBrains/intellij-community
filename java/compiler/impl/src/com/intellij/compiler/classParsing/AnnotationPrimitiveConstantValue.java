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

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 2, 2004
 */
public class AnnotationPrimitiveConstantValue extends ConstantValue{
  private final char myValueTag;
  private final ConstantValue myValue;

  public AnnotationPrimitiveConstantValue(char valueTag, ConstantValue value) {
    myValueTag = valueTag;
    myValue = value;
  }

  public AnnotationPrimitiveConstantValue(DataInput in) throws IOException {
    myValueTag = in.readChar();
    myValue = MemberInfoExternalizer.loadConstantValue(in);
  }

  public char getValueTag() {
    return myValueTag;
  }

  public ConstantValue getValue() {
    return myValue;
  }

  public void save(DataOutput out) throws IOException {
    out.writeChar(myValueTag);
    MemberInfoExternalizer.saveConstantValue(out, myValue);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AnnotationPrimitiveConstantValue)) return false;

    final AnnotationPrimitiveConstantValue memberValue = (AnnotationPrimitiveConstantValue)o;

    if (myValueTag != memberValue.myValueTag) return false;
    if (!myValue.equals(memberValue.myValue)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (int)myValueTag;
    result = 29 * result + myValue.hashCode();
    return result;
  }

}
