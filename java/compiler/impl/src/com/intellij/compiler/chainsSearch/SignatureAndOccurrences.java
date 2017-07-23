/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.compiler.chainsSearch;

import org.jetbrains.annotations.NotNull;

public class SignatureAndOccurrences implements Comparable<SignatureAndOccurrences> {
  private final MethodIncompleteSignature mySignature;
  private final int myOccurrences;

  public SignatureAndOccurrences(final MethodIncompleteSignature signature, final int occurrences) {
    mySignature = signature;
    myOccurrences = occurrences;
  }

  public MethodIncompleteSignature getSignature() {
    return mySignature;
  }

  public int getOccurrenceCount() {
    return myOccurrences;
  }

  @Override
  public int compareTo(@NotNull final SignatureAndOccurrences that) {
    final int sub = -getOccurrenceCount() + that.getOccurrenceCount();
    if (sub != 0) {
      return sub;
    }
    return mySignature.hashCode() - that.mySignature.hashCode();
  }

  @Override
  public String toString() {
    return getOccurrenceCount() + " for " + mySignature;
  }
}
