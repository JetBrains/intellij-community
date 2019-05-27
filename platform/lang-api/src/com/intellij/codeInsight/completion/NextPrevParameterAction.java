// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 * @author Evgeny Gerashchenko
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.codeInsight.actions.CodeInsightEditorAction;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class NextPrevParameterAction extends CodeInsightAction {
  private static final LanguageExtension<TemplateParameterTraversalPolicy> EP = new LanguageExtension<>("com.intellij.templateParameterTraversalPolicy");
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
    return hasSuitablePolicy(editor, file);
  }

  public static boolean hasSuitablePolicy(Editor editor, PsiFile file) {
    return findSuitableTraversalPolicy(editor, file) != null;
  }

  @Override
  public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    if (file != null && findPolicyForFile(file) != null) {
      CodeInsightEditorAction.beforeActionPerformedUpdate(e);
    }
    update(e);
  }

  @Nullable
  private static TemplateParameterTraversalPolicy findSuitableTraversalPolicy(Editor editor, PsiFile file) {
    TemplateParameterTraversalPolicy policy = findPolicyForFile(file);
    return policy != null && policy.isValidForFile(editor, file) ? policy : null;
  }

  private static TemplateParameterTraversalPolicy findPolicyForFile(PsiFile file) {
    return EP.forLanguage(file.getLanguage());
  }

  private class Handler implements CodeInsightActionHandler {
    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
      TemplateParameterTraversalPolicy policy = findSuitableTraversalPolicy(editor, file);
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
