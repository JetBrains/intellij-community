package com.intellij.packaging.ui;

import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface ArtifactEditorContext extends PackagingEditorContext {

  void queueValidation();

  @NotNull
  ArtifactType getArtifactType();
}
