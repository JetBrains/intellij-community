// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInsight.hint.ImplementationViewElement;
import com.intellij.codeInsight.hint.PsiImplementationViewElement;
import com.intellij.codeInsight.hint.actions.ShowImplementationsAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class ShowImplementationsTestUtil {
  public static PsiElement[] getImplementations() {
    return getImplementations(DataManager.getInstance().getDataContext());
  }

  public static PsiElement[] getImplementations(DataContext context) {
    final Ref<List<ImplementationViewElement>> ref = new Ref<>();
    new ShowImplementationsAction() {
      @Override
      protected void showImplementations(@NotNull List<ImplementationViewElement> impls,
                                         @NotNull Project project,
                                         String text,
                                         Editor editor,
                                         PsiFile file,
                                         PsiElement element,
                                         boolean invokedFromEditor,
                                         boolean invokedByShortcut) {
        ref.set(impls);
      }
    }.performForContext(context);
    return ContainerUtil.map2Array(ref.get(), PsiElement.class, (element) -> ((PsiImplementationViewElement) element).getPsiElement());
  }
}
