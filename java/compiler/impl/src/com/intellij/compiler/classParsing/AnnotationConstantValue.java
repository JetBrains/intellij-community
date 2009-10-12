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
public class AnnotationConstantValue extends ConstantValue {
  public static final AnnotationConstantValue[] EMPTY_ARRAY = new AnnotationConstantValue[0];
  public static final AnnotationConstantValue[][] EMPTY_ARRAY_ARRAY = new AnnotationConstantValue[0][];
  public final int myQName;
  public final AnnotationNameValuePair[] myMemberValues;

  public AnnotationConstantValue(int qName, AnnotationNameValuePair[] memberValues) {
    myQName = qName;
    myMemberValues = memberValues;
  }

  public int getAnnotationQName() {
    return myQName;
  }

  /**
   * @return an array of Integer -> ConstantValue pairs
   */
  public AnnotationNameValuePair[] getMemberValues() {
    return myMemberValues;
  }

  public AnnotationConstantValue(DataInput in) throws IOException {
    myQName = in.readInt();
    final int size = in.readInt();
    myMemberValues = new AnnotationNameValuePair[size];
    for (int idx = 0; idx < myMemberValues.length; idx++) {
      final int name = in.readInt();
      final ConstantValue constantValue = MemberInfoExternalizer.loadConstantValue(in);
      myMemberValues[idx] = new AnnotationNameValuePair(name, constantValue);
    }
  }

  public void save(DataOutput out) throws IOException {
    out.writeInt(myQName);
    out.writeInt(myMemberValues.length);
    for (int idx = 0; idx < myMemberValues.length; idx++) {
      final AnnotationNameValuePair nameValuePair = myMemberValues[idx];
      out.writeInt(nameValuePair.getName());
      MemberInfoExternalizer.saveConstantValue(out, nameValuePair.getValue());
    }
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AnnotationConstantValue)) return false;

    final AnnotationConstantValue annotationConstantValue = (AnnotationConstantValue)o;

    if (myQName != annotationConstantValue.myQName) return false;
    if (!Arrays.equals(myMemberValues, annotationConstantValue.myMemberValues)) return false;

    return true;
  }

  public int hashCode() {
    return myQName;
  }
}
