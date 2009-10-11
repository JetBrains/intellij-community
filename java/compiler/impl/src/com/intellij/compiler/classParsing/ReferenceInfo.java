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
 * @author Eugene Zhuravlev
 */
package com.intellij.compiler.classParsing;

import org.jetbrains.annotations.NonNls;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class ReferenceInfo {
  private final int myClassName;

  public ReferenceInfo(int declaringClassName) {
    myClassName = declaringClassName;
  }

  public ReferenceInfo(DataInput in) throws IOException {
    this(in.readInt());
  }

  public @NonNls String toString() { // for debug purposes
    return "Class reference[class name=" + String.valueOf(getClassName()) + "]";
  }

  public void save(DataOutput out) throws IOException {
    out.writeInt(myClassName);
  }

  public int getClassName() {
    return myClassName;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ReferenceInfo that = (ReferenceInfo)o;

    if (myClassName != that.myClassName) return false;

    return true;
  }

  public int hashCode() {
    return myClassName;
  }
}
