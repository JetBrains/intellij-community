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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.stubsHierarchy.SmartClassAnchor;
import org.jetbrains.annotations.NotNull;

public class StubClassAnchor extends SmartClassAnchor {
  public static final StubClassAnchor[] EMPTY_ARRAY = new StubClassAnchor[0];

  public final int myId;
  public final int myFileId;
  final int myStubId;

  StubClassAnchor(int symbolId, int fileId, int stubId) {
    myId = symbolId;
    myFileId = fileId;
    myStubId = stubId;
  }

  @Override
  @NotNull
  public VirtualFile retrieveFile() {
    return AnchorRepository.retrieveFile(myFileId);
  }

  @Override
  @NotNull
  public PsiClass retrieveClass(@NotNull Project project) {
    return AnchorRepository.retrieveClass(project, myFileId, myStubId);
  }

  @Override
  public int hashCode() {
    return myId;
  }

  @Override
  public String toString() {
    return AnchorRepository.anchorToString(myStubId, myFileId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof StubClassAnchor)) return false;
    StubClassAnchor anchor = (StubClassAnchor)o;
    return myId == anchor.myId &&
           myFileId == anchor.myFileId &&
           myStubId == anchor.myStubId;
  }
}
