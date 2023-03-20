// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.infos;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A resolve result that describes deconstruction pattern inference
 */
public class PatternCandidateInfo extends CandidateInfo {
  private final @Nullable @NlsContexts.DetailedDescription String myInferenceError;

  public PatternCandidateInfo(@NotNull CandidateInfo candidate,
                              @NotNull PsiSubstitutor substitutor,
                              @Nullable @NlsContexts.DetailedDescription String inferenceError) {
    super(candidate, substitutor);
    myInferenceError = inferenceError;
  }

  public @Nullable @NlsContexts.DetailedDescription String getInferenceError() {
    return myInferenceError;
  }
}
