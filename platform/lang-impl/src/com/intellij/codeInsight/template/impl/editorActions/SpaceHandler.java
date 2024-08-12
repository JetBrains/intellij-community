// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class SpaceHandler extends TypedHandlerDelegate {
  @Override
  public @NotNull Result beforeCharTyped(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
    if (charTyped == TemplateSettings.SPACE_CHAR && TemplateManager.getInstance(project).startTemplate(editor, TemplateSettings.SPACE_CHAR)) {
      return Result.STOP;
    }

    return super.beforeCharTyped(charTyped, project, editor, file, fileType);
  }
}
