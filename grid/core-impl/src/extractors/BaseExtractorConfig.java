package com.intellij.database.extractors;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BaseExtractorConfig implements ExtractorConfig {
  private final ObjectFormatter myObjectFormatter;
  private final Project myProject;

  public BaseExtractorConfig(@NotNull ObjectFormatter formatter, @Nullable Project project) {
    myObjectFormatter = formatter;
    myProject = project;
  }

  @Override
  public @NotNull ObjectFormatter getObjectFormatter() {
    return myObjectFormatter;
  }

  @Override
  public @Nullable Project getProject() {
    return myProject;
  }
}
