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
 * created at Jan 10, 2002
 * @author Jeka
 */
package com.intellij.compiler.classParsing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class FieldInfo extends MemberInfo {
  public static final FieldInfo[] EMPTY_ARRAY = new FieldInfo[0];
  private final ConstantValue myConstantValue;

  public FieldInfo(int name, int descriptor) {
    super(name, descriptor);
    myConstantValue = ConstantValue.EMPTY_CONSTANT_VALUE;
  }

  public FieldInfo(int name, int descriptor, final int genericSignature, int flags, ConstantValue value, final AnnotationConstantValue[] runtimeVisibleAnnotations, final AnnotationConstantValue[] runtimeInvisibleAnnotations) {
    super(name, descriptor, genericSignature, flags, runtimeVisibleAnnotations, runtimeInvisibleAnnotations);
    myConstantValue = value == null ? ConstantValue.EMPTY_CONSTANT_VALUE : value;
  }

  public FieldInfo(DataInput in) throws IOException {
    super(in);
    myConstantValue = MemberInfoExternalizer.loadConstantValue(in);
  }

  public ConstantValue getConstantValue() {
    return myConstantValue;
  }

  public void save(DataOutput out) throws IOException {
    super.save(out);
    MemberInfoExternalizer.saveConstantValue(out, myConstantValue);
  }

}
