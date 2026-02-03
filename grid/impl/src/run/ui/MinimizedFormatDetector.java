package com.intellij.database.run.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MinimizedFormatDetector {
  ExtensionPointName<MinimizedFormatDetector> EP_NAME = ExtensionPointName.create("com.intellij.database.minimizedFormatDetector");

  @Nullable MinimizedFormat detectFormat(@NotNull Project project, @NotNull Document document);
}
