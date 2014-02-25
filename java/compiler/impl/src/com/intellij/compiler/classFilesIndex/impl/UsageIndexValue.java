/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.impl;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * @author Dmitry Batkovich
 */
public class UsageIndexValue implements Comparable<UsageIndexValue> {
  private final int myOccurrences;
  private final MethodIncompleteSignature myMethodIncompleteSignature;

  public UsageIndexValue(final MethodIncompleteSignature signature, final int occurrences) {
    myOccurrences = occurrences;
    myMethodIncompleteSignature = signature;
  }

  public int getOccurrences() {
    return myOccurrences;
  }

  public MethodIncompleteSignature getMethodIncompleteSignature() {
    return myMethodIncompleteSignature;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final UsageIndexValue that = (UsageIndexValue)o;

    return myOccurrences == that.myOccurrences && myMethodIncompleteSignature.equals(that.myMethodIncompleteSignature);
  }

  @Override
  public int hashCode() {
    int result = myOccurrences;
    result = 31 * result + myMethodIncompleteSignature.hashCode();
    return result;
  }

  @Override
  public int compareTo(@NotNull final UsageIndexValue that) {
    int sub = -myOccurrences + that.myOccurrences;
    if (sub != 0) return sub;
    sub = myMethodIncompleteSignature.getOwner().compareTo(that.myMethodIncompleteSignature.getOwner());
    if (sub != 0) {
      return sub;
    }
    sub = myMethodIncompleteSignature.getName().compareTo(that.myMethodIncompleteSignature.getName());
    if (sub != 0) {
      return sub;
    }
    sub = myMethodIncompleteSignature.getReturnType().compareTo(that.myMethodIncompleteSignature.getReturnType());
    if (sub != 0) {
      return sub;
    }
    if (myMethodIncompleteSignature.isStatic() && !that.myMethodIncompleteSignature.isStatic()) {
      return 1;
    }
    if (that.myMethodIncompleteSignature.isStatic() && !myMethodIncompleteSignature.isStatic()) {
      return -1;
    }
    return 0;
  }
}
