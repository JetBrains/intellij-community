/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.util.treeView.TreeAnchorizer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PsiTreeAnchorizer extends TreeAnchorizer {

  private static final Key<PsiAnchor> PSI_ANCHORIZER_ANCHOR = Key.create("PSI_ANCHORIZER_ANCHOR");

  @Override
  public Object createAnchor(Object element) {
    if (element instanceof PsiElement) {
      final PsiElement psiElement = (PsiElement)element;

      return ApplicationManager.getApplication().runReadAction(new Computable<Object>() {
        @Override
        public Object compute() {
          PsiAnchor anchor = psiElement.getUserData(PSI_ANCHORIZER_ANCHOR);
          if (!psiElement.isValid()) {
            return anchor != null ? anchor : psiElement;
          }

          if (anchor == null || anchor.retrieve() != psiElement) {
            psiElement.putUserData(PSI_ANCHORIZER_ANCHOR, anchor = PsiAnchor.create(psiElement));
          }
          return anchor;
        }
      });
    }
    return super.createAnchor(element);
  }
  @Override
  @Nullable
  public Object retrieveElement(Object pointer) {
    if (pointer instanceof PsiAnchor) {
      PsiElement retrieve = ((PsiAnchor)pointer).retrieve();
      if (retrieve == null) {
        //System.out.println("Null anchor: " + pointer);
      }
      return retrieve;
    }

    return super.retrieveElement(pointer);
  }
}
