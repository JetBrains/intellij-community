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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

/**
* User: cdr
*/
class HardElementInfo implements SmartPointerElementInfo {
  private final PsiElement myElement;
  private final Project myProject;

  public HardElementInfo(@NotNull Project project, @NotNull PsiElement element) {
    myElement = element;
    myProject = project;
  }

  public Document getDocumentToSynchronize() {
    return null;
  }

  public void documentAndPsiInSync() {
  }

  @Override
  public void fastenBelt(int offset) {
  }

  @Override
  public void unfastenBelt(int offset) {
  }

  public PsiElement restoreElement() {
    return myElement;
  }

  @Override
  public void dispose() {
  }

  @Override
  public int elementHashCode() {
    return myElement.hashCode();
  }

  @Override
  public boolean pointsToTheSameElementAs(SmartPointerElementInfo other) {
    return Comparing.equal(myElement, other.restoreElement());
  }

  @Override
  public VirtualFile getVirtualFile() {
    return PsiUtilBase.getVirtualFile(myElement);
  }

  @Override
  public Segment getRange() {
    return myElement.getTextRange();
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }
}
