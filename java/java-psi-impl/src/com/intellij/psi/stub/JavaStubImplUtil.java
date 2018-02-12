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
package com.intellij.psi.stub;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.psi.impl.source.tree.JavaElementType;

public class JavaStubImplUtil {
  public static int getMethodStubIndex(PsiMethod method) {
    if (!(method instanceof PsiMethodImpl)) return -1;
    PsiFileImpl file = (PsiFileImpl)method.getContainingFile();
    StubbedSpine spine = file.getStubbedSpine();

    int result = 0;
    for (int i = 0; i < spine.getStubCount(); i++) {
      if (spine.getStubType(i) == JavaElementType.METHOD) {
        if (spine.getStubPsi(i) == method) {
          return result;
        }
        result++;
      }
    }
    return -1;
  }
}
