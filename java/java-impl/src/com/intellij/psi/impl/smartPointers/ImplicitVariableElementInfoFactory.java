/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.ImplicitVariable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImplicitVariableElementInfoFactory implements SmartPointerElementInfoFactory {
  @Nullable
  public SmartPointerElementInfo createElementInfo(@NotNull final PsiElement element) {
    if (element instanceof ImplicitVariable && element.isValid()) {
      return new ImplicitVariableInfo((ImplicitVariable) element, element.getProject());
    }
    return null;
  }

  private static class ImplicitVariableInfo implements SmartPointerElementInfo {
    private final ImplicitVariable myVar;
    private final Project myProject;

    public ImplicitVariableInfo(@NotNull ImplicitVariable var, @NotNull Project project) {
      myVar = var;
      myProject = project;
    }

    public PsiElement restoreElement() {
      PsiIdentifier psiIdentifier = myVar.getNameIdentifier();
      if (psiIdentifier == null || psiIdentifier.isValid()) return myVar;
      return null;
    }

    @Override
    public void dispose() {
    }

    @Nullable
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

    @Override
    public int elementHashCode() {
      return myVar.hashCode();
    }

    @Override
    public boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other) {
      if (other instanceof ImplicitVariableInfo) {
        return myVar == ((ImplicitVariableInfo)other).myVar;
      }
      return Comparing.equal(restoreElement(), other.restoreElement());
    }

    @Override
    public VirtualFile getVirtualFile() {
      return PsiUtilBase.getVirtualFile(myVar);
    }

    @Override
    public Segment getRange() {
      PsiIdentifier psiIdentifier = myVar.getNameIdentifier();
      if (psiIdentifier == null || !psiIdentifier.isValid()) return null;
      return psiIdentifier.getTextRange();
    }

    @NotNull
    @Override
    public Project getProject() {
      return myProject;
    }
  }
}
