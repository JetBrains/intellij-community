package com.intellij.packaging.ui;

import com.intellij.packaging.elements.PackagingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface ArtifactValidationManager {

  ArtifactEditorContext getContext();

  void registerProblem(@NotNull String message);

  void registerProblem(@NotNull String message, @Nullable ArtifactProblemQuickFix quickFix);

  void registerProblem(@NotNull String message, @Nullable PackagingElement<?> place, @Nullable ArtifactProblemQuickFix quickFix);
}
