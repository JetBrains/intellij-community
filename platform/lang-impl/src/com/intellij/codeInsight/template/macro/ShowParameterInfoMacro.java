// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.hint.ShowParameterInfoHandler;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowParameterInfoMacro extends Macro {
  private static final String NAME = "showParameterInfo";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public @Nullable Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    return new InvokeActionResult(
      () -> finishTemplateAndShowParamInfo(context.getEditor(), context.getProject(), context.getStartOffset())
    );
  }

  private static void finishTemplateAndShowParamInfo(@Nullable Editor editor, @Nullable Project project, int offset) {
    if (editor == null || project == null) return;
    TemplateManager.getInstance(project).finishTemplate(editor);
    final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (psiFile == null) return;
    ApplicationManager.getApplication().invokeLater(
      () -> ShowParameterInfoHandler.invoke(project, editor, psiFile, offset, null, false));
  }
}
