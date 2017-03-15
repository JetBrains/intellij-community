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
package com.intellij.compiler.classFilesIndex.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.LightRef;
import org.jetbrains.jps.backwardRefs.NameEnumerator;
import org.jetbrains.jps.backwardRefs.SignatureData;

import java.io.IOException;

/**
 * @author Dmitry Batkovich
 */
public class MethodIncompleteSignature {
  public static final String CONSTRUCTOR_METHOD_NAME = "<init>";
  @NotNull
  private final LightRef.JavaLightMethodRef myRef;
  @NotNull
  private final SignatureData mySignatureData;
  @NotNull private final NameEnumerator myNameEnumerator;

  public MethodIncompleteSignature(@NotNull LightRef.JavaLightMethodRef ref,
                                   @NotNull SignatureData data,
                                   @NotNull NameEnumerator nameEnumerator) {
    myRef = ref;
    mySignatureData = data;
    myNameEnumerator = nameEnumerator;
  }

  public String getName() {
    return denumerate(myRef.getName());
  }

  public String getOwner() {
    return denumerate(myRef.getOwner().getName());
  }

  public String  getRawReturnType() {
    return denumerate(mySignatureData.getRawReturnType());
  }

  public int getParameterCount() {
    return myRef.getParameterCount();
  }

  public boolean isStatic() {
    return mySignatureData.isStatic();
  }

  private String denumerate(int idx) {
    try {
      return myNameEnumerator.valueOf(idx);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MethodIncompleteSignature signature = (MethodIncompleteSignature)o;

    if (!myRef.equals(signature.myRef)) return false;
    if (!mySignatureData.equals(signature.mySignatureData)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myRef.hashCode();
    result = 31 * result + mySignatureData.hashCode();
    return result;
  }
}