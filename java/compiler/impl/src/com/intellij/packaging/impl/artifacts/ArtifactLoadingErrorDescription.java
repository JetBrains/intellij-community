// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.module.ConfigurationErrorDescription;
import com.intellij.openapi.module.ConfigurationErrorType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class ArtifactLoadingErrorDescription extends ConfigurationErrorDescription {

  public static final class ArtifactErrorType extends ConfigurationErrorType {

    private static final ArtifactErrorType INSTANCE = new ArtifactErrorType();

    private ArtifactErrorType() { super(false); }

    @Override
    public @NotNull @NonNls String getFeatureType() {
      return ArtifactManager.FEATURE_TYPE;
    }

    @Override
    public @Nls @NotNull String getErrorText(int errorCount, @NlsSafe String firstElementName) {
      return JavaCompilerBundle.message("artifact.configuration.problem.text", errorCount, firstElementName);
    }
  }

  private final Project myProject;
  private final InvalidArtifact myArtifact;

  public ArtifactLoadingErrorDescription(Project project, InvalidArtifact artifact) {
    super(artifact.getName(), artifact.getErrorMessage());
    myProject = project;
    myArtifact = artifact;
  }

  @Override
  public void ignoreInvalidElement() {
    ModifiableArtifactModel model = ArtifactManager.getInstance(myProject).createModifiableModel();
    model.removeArtifact(myArtifact);
    WriteAction.run(() -> model.commit());
  }

  @Override
  public @NotNull String getIgnoreConfirmationMessage() {
    return JavaCompilerBundle.message("unknown.artifact.remove.confirmation", myArtifact.getName());
  }

  @Override
  public boolean isValid() {
    return ArtifactManager.getInstance(myProject).getAllArtifactsIncludingInvalid().contains(myArtifact);
  }

  @Override
  public @NotNull ArtifactErrorType getErrorType() {
    return ArtifactErrorType.INSTANCE;
  }
}
