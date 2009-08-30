package com.intellij.packaging.impl.ui;

import com.intellij.packaging.ui.ArtifactValidationManager;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ArtifactProblemQuickFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class ArtifactValidationManagerBase implements ArtifactValidationManager {
  private ArtifactEditorContext myContext;

  protected ArtifactValidationManagerBase(ArtifactEditorContext context) {
    myContext = context;
  }

  public ArtifactEditorContext getContext() {
    return myContext;
  }

  public void registerProblem(@NotNull String message) {
    registerProblem(message, null);
  }

  public void registerProblem(@NotNull String message, @Nullable ArtifactProblemQuickFix quickFix) {
    registerProblem(message, null, quickFix);
  }
}
