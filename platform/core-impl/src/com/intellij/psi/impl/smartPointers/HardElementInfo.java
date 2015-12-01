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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

/**
* User: cdr
*/
class HardElementInfo extends SmartPointerElementInfo {
  @NotNull
  private final PsiElement myElement;
  @NotNull
  private final Project myProject;

  public HardElementInfo(@NotNull Project project, @NotNull PsiElement element) {
    myElement = element;
    myProject = project;
  }

  @Override
  public PsiElement restoreElement() {
    return myElement;
  }

  @Override
  public PsiFile restoreFile() {
    return myElement.getContainingFile();
  }

  @Override
  public int elementHashCode() {
    return myElement.hashCode();
  }

  @Override
  public boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other) {
    return Comparing.equal(myElement, other.restoreElement());
  }

  @Override
  public VirtualFile getVirtualFile() {
    return PsiUtilCore.getVirtualFile(myElement);
  }

  @Override
  public Segment getRange() {
    return myElement.getTextRange();
  }

  @Override
  public Segment getPsiRange() {
    return getRange();
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public String toString() {
    return "hard{" + myElement + " of " + myElement.getClass() + "}";
  }
}
