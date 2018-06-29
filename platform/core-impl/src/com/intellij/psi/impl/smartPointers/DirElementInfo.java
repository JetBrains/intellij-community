/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class DirElementInfo extends SmartPointerElementInfo {
  @NotNull
  private final VirtualFile myVirtualFile;
  @NotNull
  private final Project myProject;


  DirElementInfo(@NotNull PsiDirectory directory) {
    myProject = directory.getProject();
    myVirtualFile = directory.getVirtualFile();
  }

  @Override
  PsiElement restoreElement(@NotNull SmartPointerManagerImpl manager) {
    return SelfElementInfo.restoreDirectoryFromVirtual(myVirtualFile, myProject);
  }

  @Override
  PsiFile restoreFile(@NotNull SmartPointerManagerImpl manager) {
    return null;
  }

  @Override
  int elementHashCode() {
    return myVirtualFile.hashCode();
  }

  @Override
  boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other,
                                   @NotNull SmartPointerManagerImpl manager) {
    return other instanceof DirElementInfo && Comparing.equal(myVirtualFile, ((DirElementInfo)other).myVirtualFile);
  }

  @NotNull
  @Override
  VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  @Override
  Segment getRange(@NotNull SmartPointerManagerImpl manager) {
    return null;
  }

  @Nullable
  @Override
  Segment getPsiRange(@NotNull SmartPointerManagerImpl manager) {
    return null;
  }

  @Override
  public String toString() {
    return "dir{" + myVirtualFile + "}";
  }
}
