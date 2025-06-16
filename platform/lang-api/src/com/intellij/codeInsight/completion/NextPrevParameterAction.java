// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 * @author Evgeny Gerashchenko
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link NextPrevParameterHandler}
 */
@Deprecated
public abstract class NextPrevParameterAction extends CodeInsightAction implements DumbAware {
  private final boolean myNext;

  protected NextPrevParameterAction(boolean next) {
    myNext = next;
  }

  @Override
  public @NotNull CodeInsightActionHandler getHandler() {
    return new Handler();
  }

  @Override
  public @NotNull CodeInsightActionHandler getHandler(@NotNull DataContext dataContext) {
    return new Handler();
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return NextPrevParameterHandler.hasSuitablePolicy(editor, psiFile);
  }

  public static boolean hasSuitablePolicy(Editor editor, PsiFile file) {
    return NextPrevParameterHandler.hasSuitablePolicy(editor, file);
  }

  private class Handler implements CodeInsightActionHandler {
    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
      TemplateParameterTraversalPolicy policy = NextPrevParameterHandler.findSuitableTraversalPolicy(editor, psiFile);
      if (policy != null) {
        policy.invoke(editor, psiFile, myNext);
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  public static class Next extends NextPrevParameterAction {
    public Next() {
      super(true);
    }
  }

  public static class Prev extends NextPrevParameterAction {
    public Prev() {
      super(false);
    }
  }
}
