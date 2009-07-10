package com.intellij.packaging.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface ArtifactValidationManager {

  PackagingEditorContext getContext();


  void registerProblem(@NotNull String messsage);

  void registerProblem(@NotNull String messsage, @Nullable ArtifactProblemQuickFix quickFix);
}
