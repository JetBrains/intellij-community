// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.file;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * @author Maxim.Mossienko
 */
public abstract class UpdateAddedFileProcessor {
  private static final ExtensionPointName<UpdateAddedFileProcessor> EP_NAME = ExtensionPointName.create("com.intellij.updateAddedFileProcessor");

  public abstract boolean canProcessElement(@NotNull PsiFile element);

  public abstract void update(PsiFile element, @Nullable PsiFile originalElement) throws IncorrectOperationException;

  public static @Nullable UpdateAddedFileProcessor forElement(@NotNull PsiFile element) {
    for(UpdateAddedFileProcessor processor: EP_NAME.getExtensionList()) {
      if (processor.canProcessElement(element)) {
        return processor;
      }
    }
    return null;
  }

  public static void updateAddedFiles(@NotNull Iterable<? extends PsiFile> copyPsis, @Nullable Iterable<? extends PsiFile> originals) throws IncorrectOperationException {
    Iterator<? extends PsiFile> iterator = originals != null ? originals.iterator() : null;
    for (PsiFile copyPsi : copyPsis) {
      PsiFile original = iterator != null ? (iterator.hasNext() ? iterator.next() : null) : null;
      UpdateAddedFileProcessor processor = forElement(copyPsi);
      if (processor != null) {
        TreeElement tree = (TreeElement)SourceTreeToPsiMap.psiElementToTree(copyPsi);
        if (tree != null) {
          ChangeUtil.encodeInformation(tree);
        }
        processor.update(copyPsi, original);
        if (tree != null) {
          ChangeUtil.decodeInformation(tree);
        }
      }
    }
  }
}
