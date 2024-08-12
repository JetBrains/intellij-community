// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public abstract class UnwrapDescriptorBase implements UnwrapDescriptor {
  private Unwrapper[] myUnwrappers;

  public final Unwrapper @NotNull [] getUnwrappers() {
    if (myUnwrappers == null) {
      myUnwrappers = createUnwrappers();
    }

    return myUnwrappers;
  }

  @Override
  public @NotNull List<Pair<PsiElement, Unwrapper>> collectUnwrappers(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement e = findTargetElement(editor, file);

     List<Pair<PsiElement, Unwrapper>> result = new ArrayList<>();
     Set<PsiElement> ignored = new HashSet<>();
     while (e != null) {
       for (Unwrapper u : getUnwrappers()) {
         if (u.isApplicableTo(e) && !ignored.contains(e)) {
           result.add(Pair.create(e, u));
           u.collectElementsToIgnore(e, ignored);
         }
       }
       e = e.getParent();
     }

     return result;
  }

  protected abstract Unwrapper[] createUnwrappers();

  protected @Nullable PsiElement findTargetElement(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement endElement = file.findElementAt(offset);
    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection() && selectionModel.getSelectionStart() < offset) {
      PsiElement startElement = file.findElementAt(selectionModel.getSelectionStart());
      if (startElement != null && startElement != endElement && startElement.getTextRange().getEndOffset() == offset) {
        return startElement;
      }
    }
    return endElement;
  }

  @Override
  public boolean showOptionsDialog() {
    return true;
  }

  @Override
  public boolean shouldTryToRestoreCaretPosition() {
    return true;
  }
}
