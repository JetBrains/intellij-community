package com.intellij.diff.merge.external;

import com.intellij.diff.merge.MergeRequest;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AutomaticExternalMergeTool {
  ExtensionPointName<AutomaticExternalMergeTool> EP_NAME = ExtensionPointName.create("com.intellij.diff.merge.external.AutomaticExternalMergeTool");

  boolean canShow(@Nullable Project project, @NotNull MergeRequest request);
  void show(@Nullable Project project, @NotNull MergeRequest request);
}
