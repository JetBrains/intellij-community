// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class RenamePsiElementProcessor extends RenamePsiElementProcessorBase {
  @NotNull
  public RenameDialog createRenameDialog(@NotNull Project project,
                                         @NotNull PsiElement element,
                                         @Nullable PsiElement nameSuggestionContext,
                                         @Nullable Editor editor) {
    return new RenameDialog(project, element, nameSuggestionContext, editor);
  }

  @Override
  public RenameRefactoringDialog createDialog(@NotNull Project project,
                                                             @NotNull PsiElement element,
                                                             @Nullable PsiElement nameSuggestionContext,
                                                             @Nullable Editor editor) {
    return this.createRenameDialog(project, element, nameSuggestionContext, editor);
  }

  @NotNull
  public static RenamePsiElementProcessor forElement(@NotNull PsiElement element) {
    for (RenamePsiElementProcessorBase processor : EP_NAME.getExtensionList()) {
      if (processor.canProcessElement(element)) {
        return (RenamePsiElementProcessor)processor;
      }
    }
    return DEFAULT;
  }

  @NotNull
  public static List<RenamePsiElementProcessor> allForElement(@NotNull PsiElement element) {
    final List<RenamePsiElementProcessor> result = new ArrayList<>();
    for (RenamePsiElementProcessorBase processor : EP_NAME.getExtensions()) {
      if (processor.canProcessElement(element)) {
        result.add((RenamePsiElementProcessor)processor);
      }
    }
    return result;
  }

  private static class MyRenamePsiElementProcessor extends RenamePsiElementProcessor implements DefaultRenamePsiElementProcessor {
    @Override
    public boolean canProcessElement(@NotNull final PsiElement element) {
      return true;
    }
  }

  public static final RenamePsiElementProcessor DEFAULT = new MyRenamePsiElementProcessor();
}
