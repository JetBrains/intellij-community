// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.paths;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class StaticPathReferenceProvider extends PathReferenceProviderBase {

  private boolean myEndingSlashNotAllowed;
  private boolean myRelativePathsAllowed;
  private final FileType[] mySuitableFileTypes;

  public StaticPathReferenceProvider(final FileType @Nullable [] suitableFileTypes) {
    mySuitableFileTypes = suitableFileTypes;
  }

  @Override
  public boolean createReferences(final @NotNull PsiElement psiElement,
                                  final int offset,
                                  final String text,
                                  final @NotNull List<? super PsiReference> references,
                                  final boolean soft) {
    FileReferenceSet set = new FileReferenceSet(text, psiElement, offset, null, true, myEndingSlashNotAllowed, mySuitableFileTypes) {
      @Override
      protected boolean isUrlEncoded() {
        return true;
      }

      @Override
      protected boolean isSoft() {
        return soft;
      }
    };
    if (!myRelativePathsAllowed) {
      set.addCustomization(FileReferenceSet.DEFAULT_PATH_EVALUATOR_OPTION, FileReferenceSet.ABSOLUTE_TOP_LEVEL);
    }
    Collections.addAll(references, set.getAllReferences());
    return true;
  }

  @Override
  public @Nullable PathReference getPathReference(final @NotNull String path, final @NotNull PsiElement element) {
    final List<PsiReference> list = new SmartList<>();
    createReferences(element, list, true);
    if (list.isEmpty()) return null;

    final PsiElement target = list.get(list.size() - 1).resolve();
    if (target == null) return null;

    return new PathReference(path, PathReference.ResolveFunction.NULL_RESOLVE_FUNCTION) {
      @Override
      public PsiElement resolve() {
        return target;
      }
    };

  }

  public void setEndingSlashNotAllowed(final boolean endingSlashNotAllowed) {
    myEndingSlashNotAllowed = endingSlashNotAllowed;
  }

  public void setRelativePathsAllowed(final boolean relativePathsAllowed) {
    myRelativePathsAllowed = relativePathsAllowed;
  }
}
