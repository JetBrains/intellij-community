// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.actions;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class CreateFromTemplateManager {
  public static void startLiveTemplate(@NotNull PsiFile file) {
    startLiveTemplate(file, Collections.emptyMap());
  }

  public static void startLiveTemplate(@NotNull PsiFile file, @NotNull Map<String, String> defaultValues) {
    Editor editor = EditorHelper.openInEditor(file);
    if (editor == null) return;

    TemplateImpl template = new TemplateImpl("", file.getText(), "");
    template.setInline(true);
    int count = template.getSegmentsCount();
    if (count == 0) return;

    // Using LinkedHashSet for a saving variables orders
    Set<String> variables = new LinkedHashSet<>();
    for (int i = 0; i < count; i++) {
      variables.add(template.getSegmentName(i));
    }
    variables.removeAll(TemplateImpl.INTERNAL_VARS_SET);
    for (String variable : variables) {
      String defaultValue = defaultValues.getOrDefault(variable, variable);
      template.addVariable(variable, null, '"' + defaultValue + '"', true);
    }

    Project project = file.getProject();
    WriteCommandAction.runWriteCommandAction(project, () -> editor.getDocument().setText(template.getTemplateText()));

    editor.getCaretModel().moveToOffset(0);  // ensures caret at the start of the template
    TemplateManager.getInstance(project).startTemplate(editor, template);
  }
}
