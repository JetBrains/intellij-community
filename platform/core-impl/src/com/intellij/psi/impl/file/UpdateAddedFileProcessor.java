// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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

  /**
   * Tells whether the added file must keep the reference targets that it had before {@link #update}.
   *
   * <p>An update can change the package of the file, and that changes what a short reference resolves to. A
   * {@code true} makes {@link #updateAddedFiles} record the target of every reference before the update and restore it
   * afterwards, through {@link ChangeUtil#encodeInformation} and {@link ChangeUtil#decodeInformation}. That round trip
   * resolves every reference of the file, so it is expensive.</p>
   *
   * <p>Answer {@code false} when the update cannot change what a reference means.</p>
   */
  public boolean mustKeepReferences(@NotNull PsiFile element, @Nullable PsiFile originalElement) {
    return true;
  }

  public static @Nullable UpdateAddedFileProcessor forElement(@NotNull PsiFile element) {
    for(UpdateAddedFileProcessor processor: EP_NAME.getExtensionList()) {
      if (processor.canProcessElement(element)) {
        return processor;
      }
    }
    return null;
  }

  public static void updateAddedFiles(@NotNull Iterable<? extends PsiFile> copyPsis, @NotNull Iterable<? extends PsiFile> originals) throws IncorrectOperationException {
    Iterator<? extends PsiFile> iterator = originals.iterator();
    for (PsiFile copyPsi : copyPsis) {
      PsiFile original = iterator.hasNext() ? iterator.next() : null;
      UpdateAddedFileProcessor processor = forElement(copyPsi);
      if (processor != null) {
        TreeElement tree =
          processor.mustKeepReferences(copyPsi, original) ? (TreeElement)SourceTreeToPsiMap.psiElementToTree(copyPsi) : null;
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
