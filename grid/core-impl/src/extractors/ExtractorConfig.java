package com.intellij.database.extractors;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ExtractorConfig {
  @NotNull ObjectFormatter getObjectFormatter();

  @Nullable Project getProject();
}
