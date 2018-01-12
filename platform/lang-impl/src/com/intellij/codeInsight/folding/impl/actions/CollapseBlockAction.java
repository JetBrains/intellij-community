/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.folding.CollapseBlockHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class CollapseBlockAction  extends BaseCodeInsightAction {
  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new CodeInsightActionHandler() {
      public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
        CollapseBlockHandler.EP_NAME
          .allForLanguage(file.getLanguage())
          .forEach(handler->handler.invoke(project, editor, file));
      }
    };
  }
}
