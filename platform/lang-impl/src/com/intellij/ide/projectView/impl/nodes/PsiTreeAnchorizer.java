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
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PsiTreeAnchorizer extends TreeAnchorizer {

  private static final Key<SmartPsiElementPointer> PSI_ANCHORIZER_POINTER = Key.create("PSI_ANCHORIZER_ANCHOR");

  @Override
  public Object createAnchor(Object element) {
    if (element instanceof PsiElement) {
      final PsiElement psiElement = (PsiElement)element;

      return ApplicationManager.getApplication().runReadAction(new Computable<Object>() {
        @Override
        public Object compute() {
          SmartPsiElementPointer pointer = psiElement.getUserData(PSI_ANCHORIZER_POINTER);
          if (!psiElement.isValid()) {
            return pointer != null ? pointer : psiElement;
          }

          if (pointer == null || pointer.getElement() != psiElement) {
            pointer = SmartPointerManager.getInstance(psiElement.getProject()).createSmartPsiElementPointer(psiElement);
            psiElement.putUserData(PSI_ANCHORIZER_POINTER, pointer);
          }
          return pointer;
        }
      });
    }
    return super.createAnchor(element);
  }
  @Override
  @Nullable
  public Object retrieveElement(Object pointer) {
    if (pointer instanceof SmartPsiElementPointer) {
      return ((SmartPsiElementPointer)pointer).getElement();
    }

    return super.retrieveElement(pointer);
  }
}
