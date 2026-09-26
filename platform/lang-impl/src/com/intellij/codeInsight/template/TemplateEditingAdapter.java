// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.TemplateState;
import org.jetbrains.annotations.NotNull;

public abstract class TemplateEditingAdapter implements TemplateEditingListener {

  @Override
  public void beforeTemplateFinished(final @NotNull TemplateState state, final Template template) {
  }

  @Override
  public void templateFinished(@NotNull Template template, boolean brokenOff) {
  }

  @Override
  public void templateCancelled(Template template) {
  }

  @Override
  public void currentVariableChanged(@NotNull TemplateState templateState, Template template, int oldIndex, int newIndex) {
  }

  @Override
  public void waitingForInput(Template template) {
  }
}
