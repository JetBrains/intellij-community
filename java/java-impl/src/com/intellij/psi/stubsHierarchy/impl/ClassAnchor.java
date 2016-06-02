/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.stubs.IStubElementType;

public abstract class ClassAnchor {
  final int myFileId;

  public static final ClassAnchor[] EMPTY_ARRAY = new ClassAnchor[0];

  private ClassAnchor(int fileId) {
    myFileId = fileId;
  }

  public static class StubClassAnchor extends ClassAnchor {
    final int myStubId;
    final IStubElementType myStubElementType;

    StubClassAnchor(int fileId, int stubId, IStubElementType stubElementType) {
      super(fileId);
      myStubId = stubId;
      myStubElementType = stubElementType;
    }
  }

  static class DirectClassAnchor extends ClassAnchor {
    public final PsiClass myPsiClass;

    DirectClassAnchor(int fileId, PsiClass psiClass) {
      super(fileId);
      myPsiClass = psiClass;
    }
  }

}
