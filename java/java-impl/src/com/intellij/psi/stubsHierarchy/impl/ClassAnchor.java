/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

public abstract class ClassAnchor {
  public final int myFileId;

  public final static ClassAnchor[] EMPTY_ARRAY = new ClassAnchor[0];

  protected ClassAnchor(int fileId) {
    myFileId = fileId;
  }

  public static class StubClassAnchor extends ClassAnchor {
    public final int myStubId;

    public StubClassAnchor(int fileId, int stubId) {
      super(fileId);
      this.myStubId = stubId;
    }
  }

  public static class DirectClassAnchor extends ClassAnchor {
    public final PsiClass myPsiClass;

    public DirectClassAnchor(int fileId, PsiClass psiClass) {
      super(fileId);
      myPsiClass = psiClass;
    }
  }

}
