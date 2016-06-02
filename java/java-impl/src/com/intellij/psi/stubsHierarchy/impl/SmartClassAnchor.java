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
import com.intellij.psi.stubsHierarchy.impl.ClassAnchor.DirectClassAnchor;
import com.intellij.psi.stubsHierarchy.impl.ClassAnchor.StubClassAnchor;

public class SmartClassAnchor {

  public static final SmartClassAnchor[] EMPTY_ARRAY = new SmartClassAnchor[0];

  public final int myId;
  public final int myFileId;

  private SmartClassAnchor(int id, int fileId) {
    myId = id;
    myFileId = fileId;
  }

  static class StubSmartClassAnchor extends SmartClassAnchor {
    final int myStubId;
    final IStubElementType myStubElementType;

    StubSmartClassAnchor(int id, int fileId, int stubId, IStubElementType stubElementType) {
      super(id, fileId);
      myStubId = stubId;
      myStubElementType = stubElementType;
    }
  }

  static class DirectSmartClassAnchor extends SmartClassAnchor {
    public final PsiClass myPsiClass;

    DirectSmartClassAnchor(int id, int fileId, PsiClass psiClass) {
      super(id, fileId);
      myPsiClass = psiClass;
    }
  }

  static SmartClassAnchor create(int symbolId, ClassAnchor classAnchor) {
    if (classAnchor instanceof StubClassAnchor) {
      return new StubSmartClassAnchor(symbolId, classAnchor.myFileId, ((StubClassAnchor)classAnchor).myStubId, ((StubClassAnchor)classAnchor).myStubElementType);
    }
    if (classAnchor instanceof DirectClassAnchor) {
      return new DirectSmartClassAnchor(symbolId, classAnchor.myFileId, ((DirectClassAnchor)classAnchor).myPsiClass);
    }
    return null;
  }

}
