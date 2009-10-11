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

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 1, 2008
 */
public final class StorageMethodId extends StorageClassId{
  private final int myMethodName;
  private final int myMethodDescriptor;

  public StorageMethodId(int QName, int methodName, int methodDescriptor) {
    super(QName);
    myMethodName = methodName;
    myMethodDescriptor = methodDescriptor;
  }

  public int getMethodName() {
    return myMethodName;
  }

  public int getMethodDescriptor() {
    return myMethodDescriptor;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof StorageMethodId)) return false;

    StorageMethodId that = (StorageMethodId)o;
    return myMethodDescriptor == that.myMethodDescriptor && myMethodName == that.myMethodName && getClassQName() == that.getClassQName();
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myMethodName;
    result = 31 * result + myMethodDescriptor;
    return result;
  }
}