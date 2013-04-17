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

package com.intellij.ide;

import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiElement;

import java.awt.datatransfer.Transferable;

/**
 * @author max
 */
public class CopyPasteUtil {
  private CopyPasteUtil() { }

  public static PsiElement[] getElementsInTransferable(Transferable t) {
    final PsiElement[] elts = PsiCopyPasteManager.getElements(t);
    return elts != null ? elts : PsiElement.EMPTY_ARRAY;
  }

  public static class DefaultCopyPasteListener implements CopyPasteManager.ContentChangedListener {
    private final AbstractTreeUpdater myUpdater;

    public DefaultCopyPasteListener(final AbstractTreeUpdater updater) {
      myUpdater = updater;
    }

    @Override
    public void contentChanged(final Transferable oldTransferable, final Transferable newTransferable) {
      updateByTransferable(oldTransferable);
      updateByTransferable(newTransferable);
    }

    private void updateByTransferable(final Transferable t) {
      final PsiElement[] psiElements = CopyPasteUtil.getElementsInTransferable(t);
      for (PsiElement psiElement : psiElements) {
        if (!psiElement.getProject().isDisposed()) {
          myUpdater.addSubtreeToUpdateByElement(psiElement);
        }
      }
    }
  }
}
