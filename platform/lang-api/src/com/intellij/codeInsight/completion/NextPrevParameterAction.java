// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 * @author Evgeny Gerashchenko
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link NextPrevParameterHandler}
 */
@Deprecated(forRemoval = true)
public abstract class NextPrevParameterAction extends CodeInsightAction implements DumbAware {
  private final boolean myNext;

  protected NextPrevParameterAction(boolean next) {
    myNext = next;
  }

  @NotNull
  @Override
  public CodeInsightActionHandler getHandler() {
    return new Handler();
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return NextPrevParameterHandler.hasSuitablePolicy(editor, file);
  }

  public static boolean hasSuitablePolicy(Editor editor, PsiFile file) {
    return NextPrevParameterHandler.hasSuitablePolicy(editor, file);
  }

  private class Handler implements CodeInsightActionHandler {
    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
      TemplateParameterTraversalPolicy policy = NextPrevParameterHandler.findSuitableTraversalPolicy(editor, file);
      if (policy != null) {
        policy.invoke(editor, file, myNext);
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
