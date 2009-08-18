package com.intellij.packaging.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface ArtifactValidationManager {

  ArtifactEditorContext getContext();

  void registerProblem(@NotNull String message);

  void registerProblem(@NotNull String message, @Nullable ArtifactProblemQuickFix quickFix);
}
