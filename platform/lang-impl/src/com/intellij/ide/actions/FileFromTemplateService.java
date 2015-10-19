package com.intellij.ide.actions;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface FileFromTemplateService {
  CreateFileFromTemplateDialog.Builder createDialog(@NotNull final Project project);
}
