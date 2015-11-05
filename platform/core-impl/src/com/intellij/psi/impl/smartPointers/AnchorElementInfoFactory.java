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
package com.intellij.psi.impl.smartPointers;

import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnchorElementInfoFactory extends SmartPointerElementInfoFactory {
  @Override
  @Nullable
  public SmartPointerElementInfo createElementInfo(@NotNull PsiElement element, @NotNull PsiFile containingFile) {
    if (element instanceof StubBasedPsiElement && containingFile instanceof PsiFileWithStubSupport) {
      PsiFileWithStubSupport stubFile = (PsiFileWithStubSupport)containingFile;
      StubTree stubTree = stubFile.getStubTree();
      if (stubTree != null) {
        // use stubs when tree is not loaded
        StubBasedPsiElement stubPsi = (StubBasedPsiElement)element;
        int stubId = PsiAnchor.calcStubIndex(stubPsi);
        IStubElementType myStubElementType = stubPsi.getElementType();

        IStubFileElementType elementTypeForStubBuilder = ((PsiFileImpl)containingFile).getElementTypeForStubBuilder();
        if (stubId != -1 && elementTypeForStubBuilder != null) { // TemplateDataElementType is not IStubFileElementType
          return new AnchorElementInfo(element, stubFile, stubId, myStubElementType);
        }
      }
    }

    PsiElement anchor = getAnchor(element);
    if (anchor != null) {
      return new AnchorElementInfo(anchor, containingFile);
    }
    return null;
  }

  @Nullable
  static PsiElement getAnchor(@NotNull PsiElement element) {
    PsiUtilCore.ensureValid(element);
    if (!element.isPhysical()) return null;

    for (SmartPointerAnchorProvider provider : SmartPointerAnchorProvider.EP_NAME.getExtensions()) {
      PsiElement anchor = provider.getAnchor(element);
      if (anchor != null && anchor.isPhysical() && AnchorElementInfo.restoreFromAnchor(anchor) == element) {
        return anchor;
      }
    }
    return null;
  }
}
