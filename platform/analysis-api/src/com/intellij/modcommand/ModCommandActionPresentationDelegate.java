// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

final class ModCommandActionPresentationDelegate implements ModCommandAction, ReportingClassSubstitutor {
  private final @NotNull ModCommandAction myAction;
  private final @NotNull UnaryOperator<@NotNull Presentation> myPresentationModifier;

  ModCommandActionPresentationDelegate(@NotNull ModCommandAction action, @NotNull UnaryOperator<@NotNull Presentation> presentationModifier) {
    myAction = action;
    myPresentationModifier = presentationModifier;
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    Presentation presentation = myAction.getPresentation(context);
    return presentation == null ? null : myPresentationModifier.apply(presentation);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return myAction.perform(context);
  }

  @Override
  public @NotNull String getFamilyName() {
    return myAction.getFamilyName();
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull ActionContext context) {
    return myAction.generatePreview(context);
  }

  @Override
  public @NotNull Class<?> getSubstitutedClass() {
    return ReportingClassSubstitutor.getClassToReport(myAction);
  }
}
